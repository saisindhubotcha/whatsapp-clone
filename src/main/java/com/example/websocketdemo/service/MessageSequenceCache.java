package com.example.websocketdemo.service;

import com.example.websocketdemo.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Redis-backed cache for message sequences.
 * Stores up to 200 messages per chat.
 * Sequence continuity is guaranteed by atomic seq_no assignment in MessageService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageSequenceCache {

    private static final int MAX_CACHE_SIZE = 200;
    private static final String SEQ_KEY_PREFIX = "seq:chat:";
    private static final String SEQ_STATS_PREFIX = "seq:stats:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RedisTemplate<String, Object> redisTemplate;

    private String seqKey(Long chatId) {
        return SEQ_KEY_PREFIX + chatId;
    }

    private String statsKey(Long chatId) {
        return SEQ_STATS_PREFIX + chatId;
    }

    /**
     * Check if cache has the expected messages for a sequence range.
     */
    public boolean hasSequenceRange(Long chatId, Long beforeSeq, Long afterSeq, int limit) {
        try {
            String key = seqKey(chatId);
            Long size = redisTemplate.opsForZSet().zCard(key);
            if (size == null || size == 0) {
                return false;
            }

            if (beforeSeq != null) {
                Long count = redisTemplate.opsForZSet().count(key, 0, beforeSeq - 1);
                return count != null && count >= limit;
            } else if (afterSeq != null) {
                Long count = redisTemplate.opsForZSet().count(key, afterSeq + 1, Long.MAX_VALUE);
                return count != null && count >= limit;
            } else {
                return size >= limit;
            }
        } catch (Exception e) {
            log.warn("Failed to check sequence range in cache: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get messages by sequence range from cache only
     */
    public List<Message> getMessagesFromCache(Long chatId, Long fromSeq, Long toSeq) {
        try {
            String key = seqKey(chatId);
            Set<Object> messageIds;

            if (fromSeq != null && toSeq != null) {
                messageIds = redisTemplate.opsForZSet().rangeByScore(key, fromSeq, toSeq);
            } else if (fromSeq != null) {
                messageIds = redisTemplate.opsForZSet().rangeByScore(key, fromSeq, Long.MAX_VALUE);
            } else if (toSeq != null) {
                messageIds = redisTemplate.opsForZSet().rangeByScore(key, 0, toSeq);
            } else {
                messageIds = redisTemplate.opsForZSet().range(key, 0, -1);
            }

            if (messageIds == null || messageIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<Message> messages = new ArrayList<>();
            for (Object obj : messageIds) {
                String msgKey = "seq:msg:" + obj;
                Message msg = (Message) redisTemplate.opsForValue().get(msgKey);
                if (msg != null) {
                    messages.add(msg);
                }
            }
            return messages;
        } catch (Exception e) {
            log.warn("Failed to get messages from cache: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get cache statistics
     */
    public CacheStats getCacheStats(Long chatId) {
        try {
            String key = seqKey(chatId);
            Long size = redisTemplate.opsForZSet().zCard(key);
            if (size == null || size == 0) {
                return new CacheStats(0, 0, 0);
            }

            Set<Object> range = redisTemplate.opsForZSet().range(key, 0, 0);
            long minSeq = range != null && !range.isEmpty() ? ((Number) range.iterator().next()).longValue() : 0;

            range = redisTemplate.opsForZSet().range(key, -1, -1);
            long maxSeq = range != null && !range.isEmpty() ? ((Number) range.iterator().next()).longValue() : 0;

            return new CacheStats(size.intValue(), minSeq, maxSeq);
        } catch (Exception e) {
            log.warn("Failed to get cache stats: {}", e.getMessage());
            return new CacheStats(0, 0, 0);
        }
    }

    /**
     * Add message to cache
     */
    public boolean addMessageAtomic(Long chatId, Message message, int attempt) {
        if (attempt > 3) {
            log.error("Failed to add message to cache after retries for chat {}", chatId);
            return false;
        }

        try {
            String key = seqKey(chatId);
            String msgKey = "seq:msg:" + message.getSeqNo();

            // Check if exists
            Boolean exists = redisTemplate.opsForZSet().score(key, message.getSeqNo()) != null;
            if (exists) {
                Message existing = (Message) redisTemplate.opsForValue().get(msgKey);
                if (existing != null && existing.getMessageId().equals(message.getMessageId())) {
                    return true; // Already exists
                }
                log.warn("Sequence collision detected: seq {} has different messageId", message.getSeqNo());
                return false;
            }

            // Add to sorted set and hash
            redisTemplate.opsForZSet().add(key, message.getSeqNo(), message.getSeqNo());
            redisTemplate.opsForValue().set(msgKey, message, CACHE_TTL);
            redisTemplate.expire(key, CACHE_TTL);

            // Evict oldest if needed
            Long size = redisTemplate.opsForZSet().zCard(key);
            if (size != null && size > MAX_CACHE_SIZE) {
                redisTemplate.opsForZSet().removeRange(key, 0, size - MAX_CACHE_SIZE - 1);
            }

            log.debug("Added message seq {} to cache for chat {}", message.getSeqNo(), chatId);
            return true;

        } catch (Exception e) {
            log.warn("Failed to add message to cache (attempt {}): {}", attempt, e.getMessage());
            try {
                Thread.sleep(50L * attempt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
            return addMessageAtomic(chatId, message, attempt + 1);
        }
    }

    /**
     * Populate cache with messages from database
     */
    public void populateCache(Long chatId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        try {
            String key = seqKey(chatId);

            for (Message message : messages) {
                if (message.getSeqNo() != null) {
                    String msgKey = "seq:msg:" + message.getSeqNo();
                    redisTemplate.opsForZSet().add(key, message.getSeqNo(), message.getSeqNo());
                    redisTemplate.opsForValue().set(msgKey, message, CACHE_TTL);
                }
            }

            redisTemplate.expire(key, CACHE_TTL);

            // Trim to max size
            Long size = redisTemplate.opsForZSet().zCard(key);
            if (size != null && size > MAX_CACHE_SIZE) {
                redisTemplate.opsForZSet().removeRange(key, 0, size - MAX_CACHE_SIZE - 1);
            }

            log.info("Populated cache for chat {} with {} messages", chatId, messages.size());

        } catch (Exception e) {
            log.error("Failed to populate cache: {}", e.getMessage());
        }
    }

    /**
     * Clear cache for a chat
     */
    public void clearCache(Long chatId) {
        try {
            String key = seqKey(chatId);
            Set<Object> seqNos = redisTemplate.opsForZSet().range(key, 0, -1);
            if (seqNos != null) {
                for (Object seqNo : seqNos) {
                    redisTemplate.delete("seq:msg:" + seqNo);
                }
            }
            redisTemplate.delete(key);
            log.info("Cleared cache for chat {}", chatId);
        } catch (Exception e) {
            log.error("Failed to clear cache: {}", e.getMessage());
        }
    }

    /**
     * Get latest seq_no from cache
     */
    public Long getLatestSeqNo(Long chatId) {
        try {
            String key = seqKey(chatId);
            Set<Object> range = redisTemplate.opsForZSet().range(key, -1, -1);
            if (range != null && !range.isEmpty()) {
                return ((Number) range.iterator().next()).longValue();
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to get latest seq no: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Cache statistics holder
     */
    public static class CacheStats {
        public final int size;
        public final long minSeqNo;
        public final long maxSeqNo;

        public CacheStats(int size, long minSeqNo, long maxSeqNo) {
            this.size = size;
            this.minSeqNo = minSeqNo;
            this.maxSeqNo = maxSeqNo;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{size=%d, range=[%d, %d]}",
                    size, minSeqNo, maxSeqNo);
        }
    }
}
