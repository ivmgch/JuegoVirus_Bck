package com.campus.viral.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

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
}
