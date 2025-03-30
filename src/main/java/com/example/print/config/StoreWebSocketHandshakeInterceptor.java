package com.example.print.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
public class StoreWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            if (request instanceof ServletServerHttpRequest) {
                ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;

                // 获取URL参数
                String query = servletRequest.getServletRequest().getQueryString();
                log.info("WebSocket连接请求，原始查询参数: {}", query);

                // 获取storeId参数
                String storeId = servletRequest.getServletRequest().getParameter("storeId");
                if (storeId != null && !storeId.isEmpty()) {
                    attributes.put("storeId", storeId);
                    log.info("WebSocket连接成功提取storeId: {}", storeId);
                } else {
                    log.warn("WebSocket连接没有提供storeId参数");
                }
            }
        } catch (Exception e) {
            log.error("处理WebSocket握手时出错", e);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 不需要实现
    }
}
