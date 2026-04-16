package com.example.websocketdemo.controller;

import com.example.websocketdemo.model.*;
import com.example.websocketdemo.service.ChatService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@AllArgsConstructor
@NoArgsConstructor
@RequestMapping("/api/v1")
public class ChatRestController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/users/register")
    public ResponseEntity<Map<String, Object>> registerUser(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            Map<String, Object> result = chatService.registerUser(username);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/users/{username}/login")
    public ResponseEntity<Map<String, Object>> loginUser(@PathVariable String username) {
        try {
            Map<String, Object> result = chatService.loginUser(username);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/chats")
    public ResponseEntity<Map<String, Object>> createChat(@RequestBody Map<String, Object> request) {
        try {
            String chatName = (String) request.get("name");
            String createdBy = (String) request.get("createdBy");
            @SuppressWarnings("unchecked")
            List<String> participants = (List<String>) request.get("participants");
            
            Map<String, Object> result = chatService.createChat(chatName, createdBy, participants);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/chats/{chatId}/join")
    public ResponseEntity<Map<String, Object>> joinChat(@PathVariable Long chatId, @RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            Map<String, Object> result = chatService.joinChat(chatId, username);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/chats/{chatId}/leave")
    public ResponseEntity<Map<String, Object>> leaveChat(@PathVariable Long chatId, @RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            Map<String, Object> result = chatService.leaveChat(chatId, username);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/chats/{chatId}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(@PathVariable Long chatId, @RequestBody Map<String, String> request) {
        try {
            String senderUsername = request.get("senderUsername");
            String content = request.get("content");
            
            Map<String, Object> result = chatService.sendMessage(chatId, senderUsername, content);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/users/{username}/chats")
    public ResponseEntity<List<Chat>> getUserChats(@PathVariable String username) {
        try {
            List<Chat> chats = chatService.getUserChats(username);
            return ResponseEntity.ok(chats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/chats/{chatId}/messages")
    public ResponseEntity<List<Message>> getChatMessages(@PathVariable Long chatId, @RequestParam String username) {
        try {
            List<Message> messages = chatService.getChatMessages(chatId, username);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/chats/{chatId}/messages/unread")
    public ResponseEntity<List<Message>> getUnreadMessages(@PathVariable Long chatId, @RequestParam String username) {
        try {
            List<Message> messages = chatService.getUnreadMessages(chatId, username);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/chats/{chatId}/read")
    public ResponseEntity<Map<String, Object>> markMessagesAsRead(@PathVariable Long chatId, @RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            chatService.markMessagesAsRead(chatId, username);
            return ResponseEntity.ok(Map.of("success", true, "message", "Messages marked as read"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/users/{username}/online")
    public ResponseEntity<Map<String, Object>> setUserOnline(@PathVariable String username) {
        try {
            chatService.setUserOnline(username);
            return ResponseEntity.ok(Map.of("success", true, "message", "User set as online"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/users/{username}/offline")
    public ResponseEntity<Map<String, Object>> setUserOffline(@PathVariable String username) {
        try {
            chatService.setUserOffline(username);
            return ResponseEntity.ok(Map.of("success", true, "message", "User set as offline"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
