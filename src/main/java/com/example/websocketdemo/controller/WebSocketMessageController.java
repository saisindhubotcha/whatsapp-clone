package com.example.websocketdemo.controller;

import com.example.websocketdemo.model.ChatMessage;
import com.example.websocketdemo.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketMessageController {

    @Autowired
    private ChatService chatService;

    @MessageMapping("/chat/send")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        // Store the message in database using the service
        try {
            Long chatId = chatMessage.getChatId();
            if (chatId != null) {
                chatService.sendMessage(chatId, chatMessage.getSender(), chatMessage.getContent(), chatMessage.getMessageId());
            } else {
                System.err.println("Chat ID is null in message: " + chatMessage);
            }
        } catch (Exception e) {
            // Log error
            System.err.println("Error storing message: " + e.getMessage());
        }
        // Return void - ChatService will handle broadcasting to chat-specific topic
    }

    @MessageMapping("/user/add")
    @SendTo("/topic/public")
    public ChatMessage addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        try {
            chatService.setUserOnline(chatMessage.getSender());
        } catch (Exception e) {
            System.err.println("Error setting user online: " + e.getMessage());
        }
        
        return chatMessage;
    }
}
