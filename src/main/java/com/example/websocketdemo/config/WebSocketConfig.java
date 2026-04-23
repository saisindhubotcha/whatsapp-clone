package com.example.websocketdemo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.*;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");


        //   Use this for enabling a Full featured broker like RabbitMQ

        /*
        registry.enableStompBrokerRelay("/topic")
                .setRelayHost("localhost")
                .setRelayPort(61613)
                .setClientLogin("guest")
                .setClientPasscode("guest");
        */
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompCommand command = (StompCommand) message.getHeaders().get("stompCommand");
                if (StompCommand.CONNECT.equals(command)) {
                    SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);
                    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                    if (sessionAttributes != null) {
                        // Extract username from native headers sent by client
                        @SuppressWarnings("unchecked")
                        List<String> usernameHeader = accessor.getNativeHeader("username");
                        if (usernameHeader != null && !usernameHeader.isEmpty()) {
                            sessionAttributes.put("username", usernameHeader.get(0));
                        }
                    }
                }
                return message;
            }
        });
    }
}
