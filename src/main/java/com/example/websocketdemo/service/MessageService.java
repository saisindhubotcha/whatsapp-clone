package com.example.websocketdemo.service;

import com.example.websocketdemo.model.Message;
import com.example.websocketdemo.repository.MessageRepository;
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

        message = messageRepository.save(message);

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

        return messageRepository.save(message);
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

}
