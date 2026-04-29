package com.example.websocketdemo.service;

import com.example.websocketdemo.model.Message;
import lombok.AllArgsConstructor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@AllArgsConstructor
public class WebSocketService {

    private final SimpMessageSendingOperations messagingTemplate;

    public void broadcastMessageToChat(Long chatId, Message message) {
        messagingTemplate.convertAndSend("/topic/chat/" + chatId, message);
    }

    public void sendStoredSubscriptions(String username, Set<Object> subscriptions) {
        List<String> destinations = new ArrayList<>();
        for (Object sub : subscriptions) {
            destinations.add(String.valueOf(sub));
        }

        Map<String, Object> reconnectMessage = new HashMap<>();
        reconnectMessage.put("type", "reconnect_subscriptions");
        reconnectMessage.put("subscriptions", destinations);

        messagingTemplate.convertAndSend("/topic/user/" + username, reconnectMessage);
    }
}
