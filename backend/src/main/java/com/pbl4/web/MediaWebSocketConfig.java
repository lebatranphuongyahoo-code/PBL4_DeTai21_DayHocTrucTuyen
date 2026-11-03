package com.pbl4.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
@org.springframework.scheduling.annotation.EnableScheduling
public class MediaWebSocketConfig implements WebSocketConfigurer {
    private final MediaWebSocketHandler handler;
    @org.springframework.beans.factory.annotation.Value("${app.allowed-origins:http://127.0.0.1:*,http://localhost:*,https://127.0.0.1:*,https://localhost:*}")
    private String[] allowedOrigins;
    public MediaWebSocketConfig(MediaWebSocketHandler handler){this.handler=handler;}
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler,"/ws/classroom").setAllowedOriginPatterns(allowedOrigins);
    }
    @org.springframework.context.annotation.Bean
    public org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean webSocketContainer() {
        var bean=new org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean();
        bean.setMaxBinaryMessageBufferSize(1_500_001);bean.setMaxTextMessageBufferSize(4096);
        bean.setAsyncSendTimeout(2000L);return bean;
    }
}
