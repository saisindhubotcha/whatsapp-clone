package com.example.websocketdemo.listener;

import com.example.websocketdemo.service.CacheService;
import com.example.websocketdemo.service.ChatService;
import com.example.websocketdemo.service.WebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Set;

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    @Autowired
    private ChatService chatService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private WebSocketService webSocketService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = (String) headerAccessor.getSessionAttributes().get("username");

        logger.info("Received a new web socket connection");

        if (username != null) {
            // Send stored subscriptions from Redis to the client for auto-resubscribe
            Set<Object> storedSubscriptions = cacheService.getUserSubscriptions(username);
            if (!storedSubscriptions.isEmpty()) {
                logger.info("User {} has {} stored subscriptions, sending for auto-resubscribe", username, storedSubscriptions.size());
                webSocketService.sendStoredSubscriptions(username, storedSubscriptions);
            }
        }
    }

    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        String destination = headerAccessor.getDestination();

        if (username != null && destination != null) {
            logger.info("User {} subscribed to {}", username, destination);
            cacheService.addSubscription(username, destination);
        }
    }

    @EventListener
    public void handleWebSocketUnsubscribeListener(SessionUnsubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        String destination = headerAccessor.getDestination();

        if (username != null && destination != null) {
            logger.info("User {} unsubscribed from {}", username, destination);
            cacheService.removeSubscription(username, destination);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        String username = (String) headerAccessor.getSessionAttributes().get("username");
        if(username != null) {
            logger.info("User Disconnected : " + username);
            if (chatService != null) {
                chatService.handleWebSocketDisconnect(username);
            } else {
                logger.warn("ChatService is null, cannot handle disconnect for user: " + username);
            }
            // Note: we do NOT clear subscriptions from Redis on disconnect
            // They persist so the user can auto-resubscribe on reconnect
            // TTL will auto-expire them if the user never reconnects
        }
    }
}
