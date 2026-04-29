package com.example.websocketdemo.service;

import com.example.websocketdemo.model.Message;
import com.example.websocketdemo.repository.MessageRepository;
import com.example.websocketdemo.sharding.ShardContext;
import com.example.websocketdemo.sharding.ShardRouter;
import com.example.websocketdemo.sharding.ShardedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final CacheService cacheService;
    private final ShardRouter shardRouter;
    private final ShardedRepository shardedRepository;

    @Value("${sharding.enabled:false}")
    private boolean shardingEnabled;

    private static final int PAGE_SIZE = 20;

    private String generateMessageId(Long chatId, String sender, String content) {
        // Use UUID for guaranteed uniqueness, avoiding race conditions from content-based hashing
        return "msg_" + chatId + "_" + sender + "_" + UUID.randomUUID().toString();
    }

    public Map<String, Object> sendMessage(Long chatId, String sender, String content, String messageId) {
        if (messageId == null) {
            messageId = generateMessageId(chatId, sender, content);
        }

        Optional<Message> existing = messageRepository.findByMessageId(messageId);
        if (existing.isPresent()) {
            return Map.of("success", true, "duplicate", true);
        }

        Message message = Message.builder()
                .chatId(chatId)
                .senderUsername(sender)
                .content(content)
                .type(Message.MessageType.CHAT)
                .createdAt(LocalDateTime.now())
                .messageId(messageId)
                .build();

        message = shardedRepository.saveMessage(message);

        return Map.of("success", true, "messageId", message.getId(), "message", message);
    }

    public Message createSystemMessage(Long chatId, String sender, String content, Message.MessageType type) {
        String messageId = type.name().toLowerCase() + "_" + System.currentTimeMillis() + "_" + sender;
        
        Message message = Message.builder()
                .chatId(chatId)
                .senderUsername(sender)
                .content(content)
                .type(type)
                .createdAt(LocalDateTime.now())
                .messageId(messageId)
                .build();

        return shardedRepository.saveMessage(message);
    }

    public List<Message> getChatMessages(Long chatId, int page) {
        PageRequest pageRequest = PageRequest.of(page, PAGE_SIZE);
        if (shardingEnabled) {
            return shardedRepository.findMessagesByChatId(chatId).stream()
                    .skip((long) page * PAGE_SIZE)
                    .limit(PAGE_SIZE)
                    .toList();
        } else {
            return messageRepository.findByChatIdOrderByCreatedAtDesc(chatId, pageRequest);
        }
    }

    public List<Message> getAllChatMessages(Long chatId) {
        if (shardingEnabled) {
            return shardedRepository.findMessagesByChatId(chatId);
        } else {
            return messageRepository.findByChatIdOrderByCreatedAtAsc(chatId);
        }
    }

    public List<Message> getMessagesAfter(Long chatId, LocalDateTime afterTime) {
        if (shardingEnabled) {
            return shardedRepository.findMessagesByChatId(chatId).stream()
                    .filter(msg -> msg.getCreatedAt().isAfter(afterTime))
                    .toList();
        } else {
            return messageRepository.findMessagesAfter(chatId, afterTime);
        }
    }

    public List<Message> getUnreadMessagesForUser(Long chatId, String username) {
        if (shardingEnabled) {
            return shardedRepository.findMessagesByChatId(chatId).stream()
                    .filter(msg -> !msg.getReadByUsers().contains(username))
                    .toList();
        } else {
            return messageRepository.findUnreadMessagesForUser(chatId, username);
        }
    }

    public void markMessagesAsRead(Long chatId, String username, LocalDateTime readTime) {
        if (shardingEnabled) {
            List<Message> messages = shardedRepository.findMessagesByChatId(chatId);
            for (Message message : messages) {
                if (!message.getReadByUsers().contains(username)) {
                    message.getReadByUsers().add(username);
                    message.setReadAt(readTime);
                    shardedRepository.saveMessage(message);
                }
            }
        } else {
            messageRepository.markMessagesAsRead(chatId, username, readTime);
        }
    }

    public Optional<Message> getMessageById(Long messageId) {
        if (shardingEnabled) {
            // For sharded queries, we need to search all shards
            List<String> allShards = shardRouter.getAllShards();
            for (String shard : allShards) {
                String originalShard = ShardContext.getShard();
                try {
                    ShardContext.setShard(shard);
                    Optional<Message> result = messageRepository.findById(messageId);
                    if (result.isPresent()) {
                        return result;
                    }
                } finally {
                    if (originalShard != null) {
                        ShardContext.setShard(originalShard);
                    } else {
                        ShardContext.clear();
                    }
                }
            }
            return Optional.empty();
        } else {
            return messageRepository.findById(messageId);
        }
    }

    public Optional<Message> getMessageByMessageId(String messageId) {
        if (shardingEnabled) {
            // For sharded queries, we need to search all shards
            List<String> allShards = shardRouter.getAllShards();
            for (String shard : allShards) {
                String originalShard = ShardContext.getShard();
                try {
                    ShardContext.setShard(shard);
                    Optional<Message> result = messageRepository.findByMessageId(messageId);
                    if (result.isPresent()) {
                        return result;
                    }
                } finally {
                    if (originalShard != null) {
                        ShardContext.setShard(originalShard);
                    } else {
                        ShardContext.clear();
                    }
                }
            }
            return Optional.empty();
        } else {
            return messageRepository.findByMessageId(messageId);
        }
    }

    public Long countMessagesInChat(Long chatId) {
        if (shardingEnabled) {
            return (long) shardedRepository.findMessagesByChatId(chatId).size();
        } else {
            return messageRepository.countByChatId(chatId);
        }
    }

    public List<Message> getRecentMessages(Long chatId, int limit) {
        if (shardingEnabled) {
            return shardedRepository.findMessagesByChatId(chatId).stream()
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .limit(limit)
                    .toList();
        } else {
            return messageRepository.findByChatIdOrderByCreatedAtDesc(chatId, PageRequest.of(0, limit));
        }
    }

    public String getLastMessageId(Long chatId) {
        List<Message> recentMessages = getRecentMessages(chatId, 1);
        return recentMessages.isEmpty() ? null : recentMessages.get(0).getMessageId();
    }

    public Message editMessage(Long messageId, String newContent, String username) {
        Optional<Message> messageOpt = getMessageById(messageId);
        if (!messageOpt.isPresent()) {
            throw new IllegalArgumentException("Message not found");
        }

        Message message = messageOpt.get();
        if (!message.getSenderUsername().equals(username)) {
            throw new IllegalArgumentException("Only sender can edit message");
        }

        message.setContent(newContent);
        message.setEditedAt(LocalDateTime.now());
        
        Message updatedMessage = shardedRepository.saveMessage(message);
        
        cacheService.updateMessageInCache(updatedMessage);
        
        return updatedMessage;
    }
}
