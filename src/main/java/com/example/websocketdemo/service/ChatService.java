package com.example.websocketdemo.service;

import com.example.websocketdemo.model.*;
import com.example.websocketdemo.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class ChatService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ChatParticipantRepository participantRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final int CACHED_MESSAGE_SIZE = 5;

    // ================= REDIS KEY =================

    private String chatKey(Long chatId) {
        return "chat:" + chatId + ":messages";
    }

    // ================= USERS =================

    public Map<String, Object> registerUser(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        if (userRepository.existsById(username)) {
            throw new IllegalArgumentException("Username already taken");
        }

        User user = User.builder()
                .username(username)
                .sessionId(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .lastSeen(LocalDateTime.now())
                .isOnline(true)
                .build();

        userRepository.save(user);

        return Map.of("success", true, "user", user);
    }

    public Map<String, Object> loginUser(String username) {

        User user = userRepository.findById(username).orElse(null);
        boolean isNew = false;

        if (user == null) {
            isNew = true;
            user = User.builder()
                    .username(username)
                    .sessionId(UUID.randomUUID().toString())
                    .createdAt(LocalDateTime.now())
                    .lastSeen(LocalDateTime.now())
                    .isOnline(true)
                    .build();
        } else {
            user.setLastSeen(LocalDateTime.now());
            user.setIsOnline(true);
        }

        userRepository.save(user);

        return Map.of("success", true, "user", user, "isNewUser", isNew);
    }

    // ================= CHAT =================

    public Map<String, Object> createChat(String chatName, String createdBy, List<String> participants) {

        if (!userRepository.existsById(createdBy)) {
            throw new IllegalArgumentException("Creator not found");
        }

        List<String> valid = new ArrayList<>();
        valid.add(createdBy);

        for (String u : participants) {
            if (!u.equals(createdBy) && userRepository.existsById(u)) {
                valid.add(u);
            }
        }

        Chat chat = Chat.builder()
                .name(chatName)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .isGroupChat(valid.size() > 2)
                .build();

        chat = chatRepository.save(chat);

        for (String u : valid) {
            participantRepository.save(ChatParticipant.builder()
                    .chat(chat)
                    .user(userRepository.findById(u).get())
                    .joinedAt(LocalDateTime.now())
                    .isAdmin(u.equals(createdBy))
                    .build());
        }

        return Map.of("success", true, "chatId", chat.getId());
    }

    // ================= MESSAGE WRITE =================

    public Map<String, Object> sendMessage(Long chatId, String sender, String content, String messageId) {

        if (!userRepository.existsById(sender)) {
            throw new IllegalArgumentException("User not found");
        }

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        if (!participantRepository.findByChatIdAndUserUsername(chatId, sender).isPresent()) {
            throw new IllegalArgumentException("User not in chat");
        }

        if (messageId == null) {
            messageId = generateMessageId(chatId, sender, content);
        }

        Optional<Message> existing = messageRepository.findByMessageId(messageId);
        if (existing.isPresent()) {
            return Map.of("success", true, "duplicate", true);
        }

        Message message = Message.builder()
                .chat(chat)
                .senderUsername(sender)
                .content(content)
                .type(Message.MessageType.CHAT)
                .createdAt(LocalDateTime.now())
                .messageId(messageId)
                .build();

        message = messageRepository.save(message);

        chat.setLastMessageAt(LocalDateTime.now());
        chatRepository.save(chat);

        updateRedisCache(chatId, message);

        messagingTemplate.convertAndSend("/topic/chat/" + chatId, message);

        return Map.of("success", true, "messageId", message.getId());
    }

    public List<Message> getUnreadMessages(Long chatId, String username) { ChatParticipant participant = participantRepository.findByChatIdAndUserUsername(chatId, username) .orElseThrow(() -> new IllegalArgumentException("User not in chat")); Long lastReadId = participant.getLastReadMessageId(); if (lastReadId == null) { return messageRepository.findByChatIdOrderByCreatedAtAsc(chatId); } return messageRepository.findMessagesAfter(chatId, messageRepository.findById(lastReadId).get().getCreatedAt()); }
    public void markMessagesAsRead(Long chatId, String username) { participantRepository.findByChatIdAndUserUsername(chatId, username) .ifPresent(participant -> { List<Message> unreadMessages = messageRepository.findUnreadMessagesForUser(chatId, username); if (!unreadMessages.isEmpty()) { messageRepository.markMessagesAsRead(chatId, username, LocalDateTime.now()); participant.setLastReadMessageId(unreadMessages.get(unreadMessages.size() - 1).getId()); participantRepository.save(participant); } }); }

    public Map<String, Object> leaveChat(Long chatId, String username) { ChatParticipant participant = participantRepository.findByChatIdAndUserUsername(chatId, username) .orElseThrow(() -> new IllegalArgumentException("User not in chat")); participant.setLeftAt(LocalDateTime.now()); participantRepository.save(participant); // Send leave message
        Message leaveMessage = Message.builder() .chat(participant.getChat()) .senderUsername(username) .content(username + " left the chat") .type(Message.MessageType.LEAVE) .createdAt(LocalDateTime.now()) .build(); messageRepository.save(leaveMessage); broadcastMessageToChat(chatId, leaveMessage); return Map.of( "success", true, "message", "Left chat successfully" ); }

    // ================= REDIS WRITE (LPUSH + TRIM) =================

    private void updateRedisCache(Long chatId, Message message) {
        try {
            String key = chatKey(chatId);

            double score = message.getCreatedAt().atZone(
                    java.time.ZoneId.systemDefault()
            ).toInstant().toEpochMilli();

            redisTemplate.opsForZSet()
                    .add(key, message, score);

            // keep only last N messages
            Long size = redisTemplate.opsForZSet().zCard(key);

            if (size != null && size > CACHED_MESSAGE_SIZE) {
                redisTemplate.opsForZSet()
                        .removeRange(key, 0, size - CACHED_MESSAGE_SIZE - 1);
            }

        } catch (Exception e) {
            System.err.println("Redis ZSET write failed: " + e.getMessage());
        }
    }

    public Map<String, Object> joinChat(Long chatId, String username) { if (!userRepository.existsById(username)) { throw new IllegalArgumentException("User not found"); } Chat chat = chatRepository.findById(chatId) .orElseThrow(() -> new IllegalArgumentException("Chat not found")); if (participantRepository.findByChatIdAndUserUsername(chatId, username).isPresent()) { throw new IllegalArgumentException("User already in chat"); } participantRepository.save(ChatParticipant.builder() .chat(chat) .user(userRepository.findById(username).get()) .joinedAt(LocalDateTime.now()) .build()); // Send join message
        Message joinMessage = Message.builder() .chat(chat) .senderUsername(username) .content(username + " joined the chat") .type(Message.MessageType.JOIN) .createdAt(LocalDateTime.now()) .build(); messageRepository.save(joinMessage);
        broadcastMessageToChat(chatId, joinMessage); return Map.of( "success", true, "message", "Joined chat successfully", "chatId", chatId ); }

    // ================= MESSAGE READ =================

    public List<Message> getChatMessages(Long chatId, String username) {

        if (!participantRepository.findByChatIdAndUserUsername(chatId, username).isPresent()) {
            throw new IllegalArgumentException("User not in chat");
        }

        String key = chatKey(chatId);

        // 🔥 get last N messages (highest score first)
        Set<Object> cached = redisTemplate.opsForZSet()
                .reverseRange(key, 0, CACHED_MESSAGE_SIZE - 1);

        if (cached != null && !cached.isEmpty()) {
            return cached.stream()
                    .map(o -> (Message) o)
                    .toList();
        }

        // fallback DB
        List<Message> messages = messageRepository
                .findByChatIdOrderByCreatedAtDesc(
                        chatId,
                        PageRequest.of(0, CACHED_MESSAGE_SIZE)
                );

        Collections.reverse(messages);

        // rebuild cache
        redisTemplate.delete(key);

        for (Message m : messages) {
            double score = m.getCreatedAt()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();

            redisTemplate.opsForZSet().add(key, m, score);
        }

        return messages;
    }

    // ================= USER CHATS =================

    public List<Chat> getUserChats(String username) {
        if (!userRepository.existsById(username)) {
            throw new IllegalArgumentException("User not found");
        }

        return chatRepository.findUserChatsOrderByLastMessage(username);
    }

    // ================= MESSAGE ID =================

    private String generateMessageId(Long chatId, String sender, String content) {

        String data = chatId + "_" + sender + "_" + content;

        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(data.getBytes());

            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }

            return "msg_" + sb;

        } catch (Exception e) {
            return "msg_" + data.hashCode();
        }
    }

    // ================= OTHER FEATURES =================

    public void setUserOnline(String username) {
        User user = userRepository.findById(username)
                .orElseThrow();

        user.setIsOnline(true);
        user.setLastSeen(LocalDateTime.now());
        userRepository.save(user);
    }

    public void setUserOffline(String username) {
        userRepository.setOffline(username, LocalDateTime.now());
    }

    private void broadcastMessageToChat(Long chatId, Message message) { messagingTemplate.convertAndSend("/topic/chat/" + chatId, message); }

    public void handleWebSocketDisconnect(String username) {
        if (username != null) {
            setUserOffline(username);
        }
    }
}