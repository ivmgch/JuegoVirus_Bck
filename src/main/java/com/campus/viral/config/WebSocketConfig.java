package com.campus.viral.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Prefijos para suscripciones salientes hacia el cliente
        registry.enableSimpleBroker("/topic", "/queue");

        // Prefijo para mensajes entrantes desde el cliente (@MessageMapping)
        registry.setApplicationDestinationPrefixes("/app");

        // Prefijo para mensajería dirigida unicast (/user/{userId}/queue/...)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-viral")
            .setAllowedOriginPatterns("*")
            .withSockJS();

        // Endpoint WebSocket puro para clientes Android nativos sin emulación SockJS
        registry.addEndpoint("/ws-viral-native")
            .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String playerUuid = accessor.getFirstNativeHeader("playerUuid");
                    if (playerUuid == null || playerUuid.isBlank()) {
                        playerUuid = accessor.getLogin();
                    }
                    if (playerUuid != null && !playerUuid.isBlank()) {
                        accessor.setUser(new StompPrincipal(playerUuid.trim()));
                    }
                }
                return message;
            }
        });
    }
}
