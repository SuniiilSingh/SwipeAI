package com.match.SwipeAI.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            userSessions.put(userId, session);
            log.info("WebSocket connected for user: {}, total active: {}", userId, userSessions.size());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.debug("Received WS message: {}", message.getPayload());
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(message.getPayload(), Map.class);
            String recipientId = (String) map.get("recipientId");
            if (recipientId != null && !recipientId.isBlank()) {
                sendMessageToUser(recipientId, map);
            }
        } catch (Exception e) {
            log.trace("Non-JSON or unforwardable WS message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            userSessions.remove(userId);
            log.info("WebSocket disconnected for user: {}", userId);
        }
    }

    public void sendMessageToUser(String userId, Object messagePayload) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String payload = objectMapper.writeValueAsString(messagePayload);
                session.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                log.error("Failed to deliver WS message to user {}", userId, e);
            }
        }
    }

    private String getUserIdFromSession(WebSocketSession session) {
        if (session.getUri() == null || session.getUri().getQuery() == null) {
            return null;
        }
        String query = session.getUri().getQuery();
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2 && "userId".equals(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }
}
