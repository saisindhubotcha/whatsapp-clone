package com.example.websocketdemo.service;

import com.example.websocketdemo.model.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class WebSocketService {

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    public void broadcastMessageToChat(Long chatId, Message message) {
        messagingTemplate.convertAndSend("/topic/chat/" + chatId, message);
    }

    public void broadcastMessageToChat(Long chatId, Object message) {
        messagingTemplate.convertAndSend("/topic/chat/" + chatId, message);
    }

    public void broadcastToUser(String username, String destination, Object message) {
        messagingTemplate.convertAndSend("/topic/user/" + username, message);
    }

    public void broadcastToAllUsers(String destination, Object message) {
        messagingTemplate.convertAndSend("/topic/all", message);
    }

    public void sendTypingIndicator(Long chatId, String username, boolean isTyping) {
        Map<String, Object> typingMessage = new HashMap<>();
        typingMessage.put("type", "typing");
        typingMessage.put("username", username);
        typingMessage.put("isTyping", isTyping);
        broadcastMessageToChat(chatId, typingMessage);
    }

    public void sendUserStatusUpdate(String username, boolean isOnline) {
        Map<String, Object> statusMessage = new HashMap<>();
        statusMessage.put("type", "user_status");
        statusMessage.put("username", username);
        statusMessage.put("isOnline", isOnline);
        broadcastToAllUsers("/topic/user_status", statusMessage);
    }
}
