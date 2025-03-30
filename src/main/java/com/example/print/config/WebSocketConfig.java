package com.example.print.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 配置消息代理，客户端需要订阅这些前缀的目的地才能收到消息
        config.enableSimpleBroker("/topic");
        // 配置客户端发送消息的前缀
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册STOMP端点，客户端通过这个端点连接服务器
        registry.addEndpoint("/print-ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new StoreWebSocketHandshakeInterceptor())
                .withSockJS()
                .setSessionCookieNeeded(false); // 添加此行避免可能的cookie问题

        log.info("STOMP端点'/print-ws'已注册");
    }
}
