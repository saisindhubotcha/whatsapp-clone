package com.example.websocketdemo.controller;

import com.example.websocketdemo.model.Chat;
import com.example.websocketdemo.model.Message;
import com.example.websocketdemo.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chat/api")
public class ChatRestController {

    @Autowired
    private ChatService chatService;

    // ================= USERS =================

    @PostMapping("/users/register")
    public ResponseEntity<Map<String, Object>> registerUser(@RequestBody Map<String, String> request) {
        try {
            return ResponseEntity.ok(chatService.registerUser(request.get("username")));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/users/{username}/login")
    public ResponseEntity<Map<String, Object>> loginUser(@PathVariable String username) {
        try {
            return ResponseEntity.ok(chatService.loginUser(username));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/users/{username}/online")
    public ResponseEntity<Map<String, Object>> setOnline(@PathVariable String username) {
        try {
            chatService.setUserOnline(username);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/users/{username}/offline")
    public ResponseEntity<Map<String, Object>> setOffline(@PathVariable String username) {
        try {
            chatService.setUserOffline(username);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ================= CHATS =================

    @PostMapping("/chats")
    public ResponseEntity<Map<String, Object>> createChat(@RequestBody Map<String, Object> request) {
        try {
            String name = (String) request.get("name");
            String createdBy = (String) request.get("createdBy");

            @SuppressWarnings("unchecked")
            List<String> participants = (List<String>) request.get("participants");

            return ResponseEntity.ok(chatService.createChat(name, createdBy, participants));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/chats/{chatId}/join")
    public ResponseEntity<Map<String, Object>> joinChat(
            @PathVariable Long chatId,
            @RequestBody Map<String, String> request) {

        try {
            return ResponseEntity.ok(chatService.joinChat(chatId, request.get("username")));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/chats/{chatId}/leave")
    public ResponseEntity<Map<String, Object>> leaveChat(
            @PathVariable Long chatId,
            @RequestBody Map<String, String> request) {

        try {
            return ResponseEntity.ok(chatService.leaveChat(chatId, request.get("username")));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ================= MESSAGES =================

    @GetMapping("/chats/{chatId}/messages")
    public ResponseEntity<List<Message>> getMessages(
            @PathVariable Long chatId,
            @RequestParam String username) {

        try {
            return ResponseEntity.ok(chatService.getChatMessages(chatId, username));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/chats/{chatId}/messages/unread")
    public ResponseEntity<List<Message>> getUnread(
            @PathVariable Long chatId,
            @RequestParam String username) {

        try {
            return ResponseEntity.ok(chatService.getUnreadMessages(chatId, username));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/chats/{chatId}/read")
    public ResponseEntity<Map<String, Object>> markRead(
            @PathVariable Long chatId,
            @RequestBody Map<String, String> request) {

        try {
            chatService.markMessagesAsRead(chatId, request.get("username"));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ================= USER CHATS =================

    @GetMapping("/users/{username}/chats")
    public ResponseEntity<List<Chat>> getChats(@PathVariable String username) {
        try {
            return ResponseEntity.ok(chatService.getUserChats(username));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}