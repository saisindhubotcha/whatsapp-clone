package com.example.websocketdemo.service;

import com.example.websocketdemo.model.Message;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
@EnableAsync
@AllArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MessageService messageService;

    private static final String MESSAGE_KEY_PREFIX = "message:";
    private static final String CHAT_MESSAGES_KEY_PREFIX = "chat:";
    private static final String USER_SUBSCRIPTIONS_PREFIX = "user:subscriptions:";
    private static final int CACHE_SIZE = 100;
    private static final Duration SUBSCRIPTION_TTL = Duration.ofMinutes(5);

    private String messageKey(String messageId) {
        return MESSAGE_KEY_PREFIX + (messageId != null ? messageId.trim() : "");
    }

    private String chatMessagesKey(Long chatId) {
        return CHAT_MESSAGES_KEY_PREFIX + chatId + ":messages";
    }

    public void refreshCacheWithRecentMessagesSync(Long chatId, List<Message> recentMessages) {
        try {
            String chatKey = chatMessagesKey(chatId);
            
            for (Message message : recentMessages) {
                String messageKey = messageKey(message.getMessageId());
                redisTemplate.opsForValue().set(messageKey.trim(), message);
                redisTemplate.expire(messageKey.trim(), Duration.ofHours(1));
                
                double score = message.getCreatedAt().atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
                redisTemplate.opsForZSet().add(chatKey.trim(), message.getMessageId(), score);
            }
            
            redisTemplate.expire(chatKey.trim(), Duration.ofHours(1));
            
        } catch (Exception e) {
            System.err.println("Synchronous cache refresh failed: " + e.getMessage());
            throw new RuntimeException("Failed to refresh cache", e);
        }
    }

    public Message getCachedMessage(String messageId) {
        try {
            String key = messageKey(messageId);
            return (Message) redisTemplate.opsForValue().get(key.trim());
        } catch (Exception e) {
            System.err.println("Redis message cache read failed: " + e.getMessage());
            return null;
        }
    }

    public List<Message> getMessagesFromCache(Long chatId, int fromIndex, int toIndex, String lastDbMessageId) {
        try {
            if (lastDbMessageId != null && !isCacheFresh(chatId, lastDbMessageId)) {
                System.out.println("Cache not fresh, falling back to database");
                return null;
            }
            
            String chatKey = chatMessagesKey(chatId);
            Set<Object> cachedMessageIds = redisTemplate.opsForZSet().reverseRange(chatKey.trim(), fromIndex, toIndex - 1);
            
            if (cachedMessageIds == null || cachedMessageIds.isEmpty()) {
                return null;
            }

            List<Message> messages = new ArrayList<>();
            
            for (Object obj : cachedMessageIds) {
                String messageId = String.valueOf(obj);
                Message cachedMessage = getCachedMessage(messageId);
                if (cachedMessage != null) {
                    messages.add(cachedMessage);
                }
            }
            
            if (messages.size() >= cachedMessageIds.size() * 0.8) {
                messages.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
                return messages;
            }
            
            return null;
            
        } catch (Exception e) {
            System.err.println("Cache read failed: " + e.getMessage());
            return null;
        }
    }

    public Long getTotalMessageCount(Long chatId) {
        try {
            String chatKey = chatMessagesKey(chatId);
            Long cachedCount = redisTemplate.opsForZSet().zCard(chatKey.trim());
            
            if (cachedCount != null && cachedCount > 0) {
                return cachedCount;
            }
            
            return null;
            
        } catch (Exception e) {
            System.err.println("Failed to get message count: " + e.getMessage());
            return null;
        }
    }

    public boolean isCacheFresh(Long chatId, String lastDbMessageId) {
        try {
            if (lastDbMessageId == null) {
                return true;
            }
            
            String chatKey = chatMessagesKey(chatId);
            Set<Object> cachedMessageIds = redisTemplate.opsForZSet().reverseRange(chatKey.trim(), 0, 0);
            
            if (cachedMessageIds == null || cachedMessageIds.isEmpty()) {
                return false;
            }
            
            String mostRecentCachedMessageId = String.valueOf(cachedMessageIds.iterator().next());
            boolean isFresh = lastDbMessageId.equals(mostRecentCachedMessageId);
            
            if (!isFresh) {
                System.out.println("Cache stale: DB has newer message (" + lastDbMessageId + 
                        ") than cache (" + mostRecentCachedMessageId + ")");
            }
            
            return isFresh;
            
        } catch (Exception e) {
            System.err.println("Cache freshness check failed: " + e.getMessage());
            return false;
        }
    }

    public void invalidateMessage(Long chatId, String messageId) {
        try {
            String messageKey = messageKey(messageId);
            String chatKey = chatMessagesKey(chatId);
            
            redisTemplate.delete(messageKey.trim());
            redisTemplate.opsForZSet().remove(chatKey.trim(), messageId);
            
        } catch (Exception e) {
            System.err.println("Failed to invalidate message cache: " + e.getMessage());
        }
    }

    public void updateMessageInCache(Message message) {
        try {
            String messageKey = messageKey(message.getMessageId());
            String chatKey = chatMessagesKey(message.getChatId());
            
            redisTemplate.opsForValue().set(messageKey.trim(), message);
            redisTemplate.expire(messageKey.trim(), Duration.ofHours(1));
            
            double score = message.getCreatedAt().atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
            redisTemplate.opsForZSet().add(chatKey.trim(), message.getMessageId(), score);
            
        } catch (Exception e) {
            System.err.println("Failed to update message in cache: " + e.getMessage());
        }
    }

    public List<Message> getMessagesWithCacheAside(Long chatId, int fromIndex, int toIndex) {
        try {
            String lastDbMessageId = messageService.getLastMessageId(chatId);
            
            if (lastDbMessageId != null && isCacheFresh(chatId, lastDbMessageId)) {
                List<Message> cachedMessages = getMessagesFromCache(chatId, fromIndex, toIndex, null);
                if (cachedMessages != null) {
                    return cachedMessages;
                }
            }
            
            List<Message> dbMessages = messageService.getChatMessages(chatId, 0);
            refreshCacheWithRecentMessagesSync(chatId, dbMessages);
            
            if (fromIndex == 0 && toIndex <= dbMessages.size()) {
                return dbMessages.subList(fromIndex, Math.min(toIndex, dbMessages.size()));
            }
            
            return dbMessages;
            
        } catch (Exception e) {
            System.err.println("Cache-aside pattern failed: " + e.getMessage());
            return messageService.getChatMessages(chatId, 0);
        }
    }

    // ================= SUBSCRIPTION TRACKING =================

    private String userSubscriptionsKey(String userId) {
        return USER_SUBSCRIPTIONS_PREFIX + userId;
    }

    public void addSubscription(String userId, String destination) {
        try {
            String key = userSubscriptionsKey(userId);
            redisTemplate.opsForSet().add(key, destination);
            redisTemplate.expire(key, SUBSCRIPTION_TTL);
        } catch (Exception e) {
            System.err.println("Failed to add subscription to Redis: " + e.getMessage());
        }
    }

    public void removeSubscription(String userId, String destination) {
        try {
            String key = userSubscriptionsKey(userId);
            redisTemplate.opsForSet().remove(key, destination);
        } catch (Exception e) {
            System.err.println("Failed to remove subscription from Redis: " + e.getMessage());
        }
    }

    public Set<Object> getUserSubscriptions(String userId) {
        try {
            String key = userSubscriptionsKey(userId);
            Set<Object> subscriptions = redisTemplate.opsForSet().members(key);
            return subscriptions != null ? subscriptions : java.util.Collections.emptySet();
        } catch (Exception e) {
            System.err.println("Failed to get subscriptions from Redis: " + e.getMessage());
            return java.util.Collections.emptySet();
        }
    }

    public void renewSubscriptionTTL(String userId) {
        try {
            String key = userSubscriptionsKey(userId);
            redisTemplate.expire(key, SUBSCRIPTION_TTL);
        } catch (Exception e) {
            System.err.println("Failed to renew subscription TTL: " + e.getMessage());
        }
    }

    public void clearUserSubscriptions(String userId) {
        try {
            String key = userSubscriptionsKey(userId);
            redisTemplate.delete(key);
        } catch (Exception e) {
            System.err.println("Failed to clear subscriptions from Redis: " + e.getMessage());
        }
    }
}
