package com.example.websocketdemo.service;

import com.example.websocketdemo.model.Chat;
import com.example.websocketdemo.model.Message;
import com.example.websocketdemo.repository.ChatRepository;
import com.example.websocketdemo.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageRepository messageRepository;
    private final CacheService cacheService;
    private final ChatRepository chatRepository;
    private final MessageSequenceCache sequenceCache;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 100;

    @Value("${sharding.enabled:false}")
    private boolean shardingEnabled;

    private static final int PAGE_SIZE = 20;

    private String generateMessageId(Long chatId, String sender, String content) {
        // Use UUID for guaranteed uniqueness, avoiding race conditions from content-based hashing
        return "msg_" + chatId + "_" + sender + "_" + UUID.randomUUID().toString();
    }

    /**
     * Send message with atomic sequence assignment and cache update.
     * Uses SERIALIZABLE isolation to ensure atomicity of seq_no increment + save.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Map<String, Object> sendMessage(Long chatId, String sender, String content, String messageId) {
        return sendMessageWithRetry(chatId, sender, content, messageId, 1);
    }

    /**
     * Retryable send message with atomic DB + cache operations
     */
    private Map<String, Object> sendMessageWithRetry(Long chatId, String sender, String content, 
                                                     String messageId, int attempt) {
        try {
            if (messageId == null) {
                messageId = generateMessageId(chatId, sender, content);
            }

            // Check for duplicate
            Optional<Message> existing = messageRepository.findByMessageId(messageId);
            if (existing.isPresent()) {
                log.info("Duplicate message detected: {}", messageId);
                return Map.of("success", true, "duplicate", true, "message", existing.get());
            }

            // Atomic seq_no assignment using SERIALIZABLE transaction
            Chat chat = chatRepository.findById(chatId)
                    .orElseThrow(() -> new IllegalArgumentException("Chat not found"));
            
            Long newSeqNo;
            if (chat.getSeqCounter() == null) {
                chat.setSeqCounter(0L);
            }
            newSeqNo = chat.getSeqCounter() + 1;
            chat.setSeqCounter(newSeqNo);
            chatRepository.save(chat);

            // Build message
            Message message = Message.builder()
                    .chatId(chatId)
                    .seqNo(newSeqNo)
                    .senderUsername(sender)
                    .content(content)
                    .type(Message.MessageType.CHAT)
                    .createdAt(LocalDateTime.now())
                    .messageId(messageId)
                    .build();

            // Save to database
            message = messageRepository.save(message);

            // Atomic cache update with retry
            boolean cacheUpdated = false;
            try {
                cacheUpdated = sequenceCache.addMessageAtomic(chatId, message, 1);
            } catch (Exception e) {
                log.warn("Cache update failed for message {}: {}", message.getSeqNo(), e.getMessage());
            }

            // If cache update failed but DB succeeded, we can still return success
            // The cache will be refreshed on next read
            log.info("Message {} sent with seq {}. Cache updated: {}", 
                    messageId, newSeqNo, cacheUpdated);

            return Map.of("success", true, "messageId", message.getId(), 
                    "message", message, "cacheUpdated", cacheUpdated);

        } catch (Exception e) {
            // Check for concurrent modification / serialization failure
            if (isConcurrencyException(e) && attempt < MAX_RETRIES) {
                log.warn("Concurrent modification detected on attempt {}. Retrying...", attempt);
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
                return sendMessageWithRetry(chatId, sender, content, messageId, attempt + 1);
            }
            throw e;
        }
    }

    /**
     * Check if exception indicates concurrent modification
     */
    private boolean isConcurrencyException(Exception e) {
        String msg = e.getMessage();
        return msg != null && (
            msg.contains("could not serialize") ||
            msg.contains("concurrent update") ||
            msg.contains("deadlock") ||
            msg.contains("lock wait timeout") ||
            msg.contains("OptimisticLockException")
        );
    }

    /**
     * Create system message with atomic operations
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Message createSystemMessage(Long chatId, String sender, String content, Message.MessageType type) {
        String messageId = type.name().toLowerCase() + "_" + System.currentTimeMillis() + "_" + sender;

        // Atomic seq_no assignment
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        Long newSeqNo;
        if (chat.getSeqCounter() == null) {
            chat.setSeqCounter(0L);
        }
        newSeqNo = chat.getSeqCounter() + 1;
        chat.setSeqCounter(newSeqNo);
        chatRepository.save(chat);

        Message message = Message.builder()
                .chatId(chatId)
                .seqNo(newSeqNo)
                .senderUsername(sender)
                .content(content)
                .type(type)
                .createdAt(LocalDateTime.now())
                .messageId(messageId)
                .build();

        message = messageRepository.save(message);

        // Update cache
        sequenceCache.addMessageAtomic(chatId, message, 1);

        return message;
    }

    public List<Message> getChatMessages(Long chatId, int page) {
        PageRequest pageRequest = PageRequest.of(page, PAGE_SIZE);

        return messageRepository.findByChatIdOrderByCreatedAtDesc(chatId, pageRequest);

    }

    public List<Message> getAllChatMessages(Long chatId) {

            return messageRepository.findByChatIdOrderByCreatedAtAsc(chatId);
    }

    public List<Message> getMessagesAfter(Long chatId, LocalDateTime afterTime) {

            return messageRepository.findMessagesAfter(chatId, afterTime);

    }

    public List<Message> getUnreadMessagesForUser(Long chatId, String username) {

            return messageRepository.findUnreadMessagesForUser(chatId, username);

    }

    public void markMessagesAsRead(Long chatId, String username, LocalDateTime readTime) {

            messageRepository.markMessagesAsRead(chatId, username, readTime);

    }

    public Optional<Message> getMessageById(Long messageId) {

            return messageRepository.findById(messageId);

    }

    public List<Message> getRecentMessages(Long chatId, int limit) {

            return messageRepository.findByChatIdOrderByCreatedAtDesc(chatId, PageRequest.of(0, limit));

    }

    public String getLastMessageId(Long chatId) {
        List<Message> recentMessages = getRecentMessages(chatId, 1);
        return recentMessages.isEmpty() ? null : recentMessages.get(0).getMessageId();
    }

    // ================= SEQUENCE-BASED PAGINATION METHODS WITH CACHE =================

    /**
     * Get messages by sequence - checks cache first, falls back to DB
     */
    public List<Message> getMessagesBySeq(Long chatId, Integer limit) {
        int pageSize = (limit != null && limit > 0) ? limit : 50;
        
        // Check cache first
        Long latestSeq = sequenceCache.getLatestSeqNo(chatId);
        if (latestSeq != null) {
            List<Message> cached = sequenceCache.getMessagesFromCache(chatId, latestSeq - pageSize + 1, latestSeq);
            if (cached.size() >= pageSize) {
                log.debug("Cache hit for getMessagesBySeq chat {}, returning {} messages", chatId, cached.size());
                // Sort descending for consistency with DB query
                cached.sort(Comparator.comparingLong(Message::getSeqNo).reversed());
                return cached;
            }
        }
        
        // Cache miss - fetch from DB
        log.debug("Cache miss for getMessagesBySeq chat {}, fetching from DB", chatId);
        List<Message> messages = messageRepository.findByChatIdOrderBySeqNoDesc(chatId, PageRequest.of(0, pageSize));
        
        // Populate cache with results
        sequenceCache.populateCache(chatId, messages);
        
        return messages;
    }

    /**
     * Get messages before a sequence - checks cache first, falls back to DB
     */
    public List<Message> getMessagesBeforeSeq(Long chatId, Long beforeSeq, Integer limit) {
        int pageSize = (limit != null && limit > 0) ? limit : 50;
        
        // Check if we have this range in cache
        if (sequenceCache.hasSequenceRange(chatId, beforeSeq, null, pageSize)) {
            List<Message> cached = sequenceCache.getMessagesFromCache(chatId, beforeSeq - pageSize, beforeSeq - 1);
            if (cached.size() >= pageSize) {
                log.debug("Cache hit for getMessagesBeforeSeq chat {}, before_seq={}, returning {} messages", 
                        chatId, beforeSeq, cached.size());
                // Sort descending for consistency
                cached.sort(Comparator.comparingLong(Message::getSeqNo).reversed());
                return cached;
            }
        }
        
        // Cache miss - fetch from DB
        log.debug("Cache miss for getMessagesBeforeSeq chat {}, before_seq={}, fetching from DB", chatId, beforeSeq);
        List<Message> messages = messageRepository.findByChatIdAndSeqNoLessThanOrderBySeqNoDesc(
                chatId, beforeSeq, PageRequest.of(0, pageSize));
        
        // Populate cache
        sequenceCache.populateCache(chatId, messages);
        
        return messages;
    }

    /**
     * Get messages after a sequence - checks cache first, falls back to DB
     */
    public List<Message> getMessagesAfterSeq(Long chatId, Long afterSeq, Integer limit) {
        int pageSize = (limit != null && limit > 0) ? limit : 50;
        
        // Check if we have this range in cache
        if (sequenceCache.hasSequenceRange(chatId, null, afterSeq, pageSize)) {
            List<Message> cached = sequenceCache.getMessagesFromCache(chatId, afterSeq + 1, afterSeq + pageSize);
            if (cached.size() >= pageSize) {
                log.debug("Cache hit for getMessagesAfterSeq chat {}, after_seq={}, returning {} messages", 
                        chatId, afterSeq, cached.size());
                // Sort ascending for consistency
                cached.sort(Comparator.comparingLong(Message::getSeqNo));
                return cached;
            }
        }
        
        // Cache miss - fetch from DB
        log.debug("Cache miss for getMessagesAfterSeq chat {}, after_seq={}, fetching from DB", chatId, afterSeq);
        List<Message> messages = messageRepository.findByChatIdAndSeqNoGreaterThanOrderBySeqNoAsc(
                chatId, afterSeq, PageRequest.of(0, pageSize));
        
        // Populate cache
        sequenceCache.populateCache(chatId, messages);
        
        return messages;
    }

    /**
     * Get latest seq_no - checks cache first, falls back to DB
     */
    public Long getLatestSeqNo(Long chatId) {
        Long cachedLatest = sequenceCache.getLatestSeqNo(chatId);
        if (cachedLatest != null) {
            log.debug("Cache hit for getLatestSeqNo chat {}: {}", chatId, cachedLatest);
            return cachedLatest;
        }
        
        Long dbLatest = messageRepository.findMaxSeqNoByChatId(chatId);
        log.debug("Cache miss for getLatestSeqNo chat {}, DB value: {}", chatId, dbLatest);
        return dbLatest;
    }

    /**
     * Get cache statistics for monitoring
     */
    public MessageSequenceCache.CacheStats getCacheStats(Long chatId) {
        return sequenceCache.getCacheStats(chatId);
    }

    /**
     * Clear cache for a chat (useful for debugging or cache invalidation)
     */
    public void clearChatCache(Long chatId) {
        sequenceCache.clearCache(chatId);
        log.info("Cleared sequence cache for chat {}", chatId);
    }

}
