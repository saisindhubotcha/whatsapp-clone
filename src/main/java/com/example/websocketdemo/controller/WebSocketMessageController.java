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

    private ChatService chatService;

    @MessageMapping("/chat/send")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {
        // Store the message in database using the service
        try {
            chatService.sendMessage(1L, chatMessage.getSender(), chatMessage.getContent());
        } catch (Exception e) {
            // Log error but still broadcast the message
            System.err.println("Error storing message: " + e.getMessage());
        }
        return chatMessage;
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
