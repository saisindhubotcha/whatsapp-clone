package com.example.websocketdemo.service;

import com.example.websocketdemo.model.Chat;
import com.example.websocketdemo.model.ChatParticipant;
import com.example.websocketdemo.model.Message;
import com.example.websocketdemo.model.User;
import com.example.websocketdemo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class ChatService {

    private final ChatParticipantRepository participantRepository;
    private final UserService userService;
    private final MessageService messageService;
    private final CacheService cacheService;
    private final ChatRepository chatRepository;
    private final WebSocketService webSocketService;


    @Value("${sharding.enabled:false}")
    private boolean shardingEnabled;

    private static final int PAGE_SIZE = 5;

    // ================= CHAT MANAGEMENT =================

    public Map<String, Object> createChat(String chatName, String createdBy, List<String> participants) {
        if (!userService.userExists(createdBy)) {
            throw new IllegalArgumentException("Creator not found");
        }

        List<String> valid = new ArrayList<>();
        valid.add(createdBy);

        // Add all participants without checking if they exist
        // Users will be auto-created when they first log in
        for (String u : participants) {
            if (!u.equals(createdBy) && !valid.contains(u)) {
                valid.add(u);
            }
        }

        // Auto-generate chat name for direct chats (1-on-1)
        String finalChatName = chatName;
        if (valid.size() == 2) {
            // Direct chat: use "fromName - toName" format
            String otherUser = valid.get(0).equals(createdBy) ? valid.get(1) : valid.get(0);
            finalChatName = createdBy + " - " + otherUser;
        }

        Chat chat = Chat.builder()
                .name(finalChatName)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .isGroupChat(valid.size() > 2)
                .build();

        chat = chatRepository.save(chat);

        for (String u : valid) {
            participantRepository.save(ChatParticipant.builder()
                    .chatId(chat.getId())
                    .userUsername(u)
                    .joinedAt(LocalDateTime.now())
                    .isAdmin(u.equals(createdBy))
                    .build());
        }

        return Map.of("success", true, "chatId", chat.getId());
    }

    // ================= MESSAGE OPERATIONS =================

    public Map<String, Object> sendMessage(Long chatId, String sender, String content, String messageId) {
        if (!userService.userExists(sender)) {
            throw new IllegalArgumentException("User not found");
        }

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        if (!participantRepository.findByChatIdAndUserUsername(chatId, sender).isPresent()) {
            throw new IllegalArgumentException("User not in chat");
        }

        Map<String, Object> result = messageService.sendMessage(chatId, sender, content, messageId);
        
        if ((Boolean) result.get("success") && !result.containsKey("duplicate")) {
            Message message = (Message) result.get("message");
            
            chat.setLastMessageAt(message.getCreatedAt());
            chat.setLastMessageId(message.getMessageId());
            chatRepository.save(chat);

            cacheService.refreshCacheWithRecentMessagesSync(chatId, messageService.getRecentMessages(chatId, 20));
            webSocketService.broadcastMessageToChat(chatId, message);
        }

        return result;
    }

    public List<Message> getUnreadMessages(Long chatId, String username) {
        ChatParticipant participant = participantRepository.findByChatIdAndUserUsername(chatId, username)
                .orElseThrow(() -> new IllegalArgumentException("User not in chat"));
        
        Long lastReadId = participant.getLastReadMessageId();
        if (lastReadId == null) {
            return messageService.getAllChatMessages(chatId);
        }
        
        Message lastReadMessage = messageService.getMessageById(lastReadId)
                .orElseThrow(() -> new IllegalStateException("Message not found for ID: " + lastReadId));
        
        return messageService.getMessagesAfter(chatId, lastReadMessage.getCreatedAt());
    }

    public void markMessagesAsRead(Long chatId, String username) {
        participantRepository.findByChatIdAndUserUsername(chatId, username)
                .ifPresent(participant -> {
                    List<Message> unreadMessages = messageService.getUnreadMessagesForUser(chatId, username);
                    if (!unreadMessages.isEmpty()) {
                        messageService.markMessagesAsRead(chatId, username, LocalDateTime.now());
                        participant.setLastReadMessageId(unreadMessages.get(unreadMessages.size() - 1).getId());
                        participantRepository.save(participant);
                    }
                });
    }


    public Map<String, Object> leaveChat(Long chatId, String username) {
        ChatParticipant participant = participantRepository.findByChatIdAndUserUsername(chatId, username)
                .orElseThrow(() -> new IllegalArgumentException("User not in chat"));
        participant.setLeftAt(LocalDateTime.now());
        participantRepository.save(participant);
        
        Message leaveMessage = messageService.createSystemMessage(chatId, username, username + " left the chat", Message.MessageType.LEAVE);
        
        Chat chat = chatRepository.findById(chatId).orElseThrow(() -> new IllegalArgumentException("Chat not found"));
        chat.setLastMessageAt(leaveMessage.getCreatedAt());
        chat.setLastMessageId(leaveMessage.getMessageId());
        chatRepository.save(chat);
        
        webSocketService.broadcastMessageToChat(chatId, leaveMessage);
        return Map.of(
                "success", true,
                "message", "Left chat successfully"
        );
    }

    public Map<String, Object> joinChat(Long chatId, String username) {
        if (!userService.userExists(username)) {
            throw new IllegalArgumentException("User not found");
        }
        
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));
                
        if (participantRepository.findByChatIdAndUserUsername(chatId, username).isPresent()) {
            throw new IllegalArgumentException("User already in chat");
        }
        
        participantRepository.save(ChatParticipant.builder()
                .chatId(chatId)
                .userUsername(username)
                .joinedAt(LocalDateTime.now())
                .build());
                
        Message joinMessage = messageService.createSystemMessage(chatId, username, username + " joined the chat", Message.MessageType.JOIN);
        
        chat.setLastMessageAt(joinMessage.getCreatedAt());
        chat.setLastMessageId(joinMessage.getMessageId());
        chatRepository.save(chat);
        
        webSocketService.broadcastMessageToChat(chatId, joinMessage);
        return Map.of(
                "success", true,
                "message", "Joined chat successfully",
                "chatId", chatId
        );
    }

    // ================= MESSAGE RETRIEVAL =================

    public List<Message> getChatMessages(Long chatId, String username) {
        return getChatMessages(chatId, username, 0);
    }

    public List<Message> getChatMessages(Long chatId, String username, int page) {
        if (!participantRepository.findByChatIdAndUserUsername(chatId, username).isPresent()) {
            throw new IllegalArgumentException("User not in chat");
        }

        if (page == 0) {
            List<Message> dbMessages = messageService.getChatMessages(chatId, 0);
            String lastDbMessageId = messageService.getLastMessageId(chatId);
            List<Message> cachedMessages = cacheService.getMessagesWithCacheAside(chatId, 0, PAGE_SIZE, lastDbMessageId, dbMessages);
            if(!cachedMessages.isEmpty()) return cachedMessages;
        }
        
        return messageService.getChatMessages(chatId, page);
    }

    // ================= USER CHATS =================

    public List<Chat> getUserChats(String username) {
        if (!userService.userExists(username)) {
            throw new IllegalArgumentException("User not found");
        }

        return chatRepository.findUserChats(username);
    }

    // ================= USER OPERATIONS (delegated to UserService) =================

    public Map<String, Object> registerUser(String username) {
        return userService.registerUser(username);
    }

    public Map<String, Object> loginUser(String username) {
        return userService.loginUser(username);
    }

    public void setUserOnline(String username) {
        userService.setUserOnline(username);
    }

    public void setUserOffline(String username) {
        userService.setUserOffline(username);
    }

    public void handleWebSocketDisconnect(String username) {
        userService.handleWebSocketDisconnect(username);
    }

    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    // ================= SEQUENCE-BASED PAGINATION METHODS =================

    public List<Message> getMessagesBySeq(Long chatId, Integer limit) {
        return messageService.getMessagesBySeq(chatId, limit);
    }

    public List<Message> getMessagesBeforeSeq(Long chatId, Long beforeSeq, Integer limit) {
        return messageService.getMessagesBeforeSeq(chatId, beforeSeq, limit);
    }

    public List<Message> getMessagesAfterSeq(Long chatId, Long afterSeq, Integer limit) {
        return messageService.getMessagesAfterSeq(chatId, afterSeq, limit);
    }

    public Long getLatestSeqNo(Long chatId) {
        return messageService.getLatestSeqNo(chatId);
    }

}