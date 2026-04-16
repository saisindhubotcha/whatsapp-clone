package com.example.websocketdemo.service;

import com.example.websocketdemo.model.*;
import com.example.websocketdemo.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public Map<String, Object> registerUser(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        if (userRepository.existsById(username)) {
            throw new IllegalArgumentException("Username already taken");
        }

        String sessionId = UUID.randomUUID().toString();
        User user = User.builder()
                .username(username)
                .sessionId(sessionId)
                .createdAt(LocalDateTime.now())
                .lastSeen(LocalDateTime.now())
                .isOnline(true)
                .build();
        
        userRepository.save(user);

        return Map.of(
            "success", true,
            "user", user,
            "message", "User registered successfully"
        );
    }

    public Map<String, Object> createChat(String chatName, String createdBy, List<String> participantUsernames) {
        if (!userRepository.existsById(createdBy)) {
            throw new IllegalArgumentException("Creator not found");
        }

        Chat chat = Chat.builder()
                .name(chatName)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .isGroupChat(participantUsernames.size() > 1)
                .build();
        
        chat = chatRepository.save(chat);

        // Add creator as participant
        participantRepository.save(ChatParticipant.builder()
                .chat(chat)
                .user(userRepository.findById(createdBy).get())
                .joinedAt(LocalDateTime.now())
                .isAdmin(true)
                .build());

        // Add other participants
        for (String username : participantUsernames) {
            if (!username.equals(createdBy) && userRepository.existsById(username)) {
                participantRepository.save(ChatParticipant.builder()
                        .chat(chat)
                        .user(userRepository.findById(username).get())
                        .joinedAt(LocalDateTime.now())
                        .build());
            }
        }

        return Map.of(
            "success", true,
            "chat", chat,
            "message", "Chat created successfully"
        );
    }

    public Map<String, Object> joinChat(Long chatId, String username) {
        if (!userRepository.existsById(username)) {
            throw new IllegalArgumentException("User not found");
        }

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        if (participantRepository.findByChatIdAndUserUsername(chatId, username).isPresent()) {
            throw new IllegalArgumentException("User already in chat");
        }

        participantRepository.save(ChatParticipant.builder()
                .chat(chat)
                .user(userRepository.findById(username).get())
                .joinedAt(LocalDateTime.now())
                .build());

        // Send join message
        Message joinMessage = Message.builder()
                .chat(chat)
                .senderUsername(username)
                .content(username + " joined the chat")
                .type(Message.MessageType.JOIN)
                .createdAt(LocalDateTime.now())
                .build();
        
        messageRepository.save(joinMessage);
        broadcastMessageToChat(chatId, joinMessage);

        return Map.of(
            "success", true,
            "message", "Joined chat successfully",
            "chatId", chatId
        );
    }

    public Map<String, Object> sendMessage(Long chatId, String senderUsername, String content) {
        if (!userRepository.existsById(senderUsername)) {
            throw new IllegalArgumentException("User not found");
        }

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        if (!participantRepository.findByChatIdAndUserUsername(chatId, senderUsername).isPresent()) {
            throw new IllegalArgumentException("User not in chat");
        }

        Message message = Message.builder()
                .chat(chat)
                .senderUsername(senderUsername)
                .content(content)
                .type(Message.MessageType.CHAT)
                .createdAt(LocalDateTime.now())
                .build();
        
        message = messageRepository.save(message);

        // Update chat's last message time
        chat.setLastMessageAt(LocalDateTime.now());
        chatRepository.save(chat);

        // Broadcast to online participants
        broadcastMessageToChat(chatId, message);

        return Map.of(
            "success", true,
            "message", "Message sent successfully",
            "messageId", message.getId(),
            "timestamp", message.getCreatedAt()
        );
    }

    public Map<String, Object> leaveChat(Long chatId, String username) {
        ChatParticipant participant = participantRepository.findByChatIdAndUserUsername(chatId, username)
                .orElseThrow(() -> new IllegalArgumentException("User not in chat"));

        participant.setLeftAt(LocalDateTime.now());
        participantRepository.save(participant);

        // Send leave message
        Message leaveMessage = Message.builder()
                .chat(participant.getChat())
                .senderUsername(username)
                .content(username + " left the chat")
                .type(Message.MessageType.LEAVE)
                .createdAt(LocalDateTime.now())
                .build();
        
        messageRepository.save(leaveMessage);
        broadcastMessageToChat(chatId, leaveMessage);

        return Map.of(
            "success", true,
            "message", "Left chat successfully"
        );
    }

    public List<Chat> getUserChats(String username) {
        return chatRepository.findUserChatsOrderByLastMessage(username);
    }

    public List<Message> getChatMessages(Long chatId, String username) {
        // Verify user is in chat
        if (!participantRepository.findByChatIdAndUserUsername(chatId, username).isPresent()) {
            throw new IllegalArgumentException("User not in chat");
        }
        
        return messageRepository.findByChatIdOrderByCreatedAtAsc(chatId);
    }

    public List<Message> getUnreadMessages(Long chatId, String username) {
        ChatParticipant participant = participantRepository.findByChatIdAndUserUsername(chatId, username)
                .orElseThrow(() -> new IllegalArgumentException("User not in chat"));
        
        Long lastReadId = participant.getLastReadMessageId();
        if (lastReadId == null) {
            return messageRepository.findByChatIdOrderByCreatedAtAsc(chatId);
        }
        
        return messageRepository.findMessagesAfter(chatId, 
                messageRepository.findById(lastReadId).get().getCreatedAt());
    }

    public void markMessagesAsRead(Long chatId, String username) {
        participantRepository.findByChatIdAndUserUsername(chatId, username)
                .ifPresent(participant -> {
                    List<Message> unreadMessages = messageRepository.findUnreadMessagesForUser(chatId, username);
                    if (!unreadMessages.isEmpty()) {
                        messageRepository.markMessagesAsRead(chatId, username, LocalDateTime.now());
                        participant.setLastReadMessageId(unreadMessages.get(unreadMessages.size() - 1).getId());
                        participantRepository.save(participant);
                    }
                });
    }

    public void setUserOnline(String username) {
        userRepository.setOnline(username);
    }

    public void setUserOffline(String username) {
        userRepository.setOffline(username, LocalDateTime.now());
    }

    private void broadcastMessageToChat(Long chatId, Message message) {
        messagingTemplate.convertAndSend("/topic/chat/" + chatId, message);
    }

    public void handleWebSocketDisconnect(String username) {
        if (username != null && userRepository.existsById(username)) {
            setUserOffline(username);
        }
    }
}
