package com.match.SwipeAI.service.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.SwipeAI.dto.NotificationDto;
import com.match.SwipeAI.model.PushToken;
import com.match.SwipeAI.model.UserNotification;
import com.match.SwipeAI.repository.PushTokenRepository;
import com.match.SwipeAI.repository.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private final PushTokenRepository pushTokenRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Transactional
    public UserNotification saveNotification(UUID userId, String type, String title, String body, Map<String, Object> data) {
        String dataJson = null;
        if (data != null && !data.isEmpty()) {
            try {
                dataJson = objectMapper.writeValueAsString(data);
            } catch (Exception e) {
                log.warn("Failed to serialize notification data: {}", e.getMessage());
            }
        }
        UserNotification notif = UserNotification.builder()
                .userId(userId)
                .type(type != null ? type : "SYSTEM")
                .title(title)
                .body(body)
                .dataJson(dataJson)
                .isRead(false)
                .build();
        return userNotificationRepository.save(notif);
    }

    @Transactional
    public void registerToken(UUID userId, String token, String platform) {
        if (token == null || token.isBlank()) return;

        Optional<PushToken> existing = pushTokenRepository.findByUserIdAndToken(userId, token);
        if (existing.isPresent()) {
            PushToken pt = existing.get();
            pt.setPlatform(platform != null ? platform : "ANDROID");
            pushTokenRepository.save(pt);
            log.info("Updated push token for user {}", userId);
        } else {
            PushToken pt = PushToken.builder()
                    .userId(userId)
                    .token(token.trim())
                    .platform(platform != null ? platform : "ANDROID")
                    .build();
            pushTokenRepository.save(pt);
            log.info("Registered new push token for user {}", userId);
        }
    }

    @Transactional
    public void unregisterToken(UUID userId, String token) {
        if (token == null || token.isBlank()) return;
        pushTokenRepository.deleteByUserIdAndToken(userId, token);
        log.info("Unregistered push token for user {}", userId);
    }

    /**
     * Dispatches push notifications asynchronously to all registered devices of a user.
     */
    public CompletableFuture<Integer> sendPushToUser(UUID userId, String title, String body, Map<String, Object> data) {
        List<PushToken> tokens = pushTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.debug("No push tokens registered for user {}", userId);
            return CompletableFuture.completedFuture(0);
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        for (PushToken pt : tokens) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("to", pt.getToken());
            msg.put("title", title);
            msg.put("body", body);
            msg.put("sound", "default");
            msg.put("channelId", "default");
            msg.put("priority", "high");
            if (data != null && !data.isEmpty()) {
                msg.put("data", data);
            }
            messages.add(msg);
        }

        return CompletableFuture.supplyAsync(() -> dispatchToExpo(messages));
    }

    /**
     * Dispatches notification when a mutual match is formed.
     */
    public void sendMatchNotification(UUID actorId, UUID targetId, UUID matchId, String actorName, String targetName) {
        Map<String, Object> data = Map.of(
                "type", "MATCH",
                "matchId", matchId.toString(),
                "url", "/(tabs)/matches"
        );

        String title = "It's a Match! ✨";
        String actorBody = "You and " + targetName + " liked each other! Answer the 10s quiz to unlock chat.";
        String targetBody = "You and " + actorName + " liked each other! Answer the 10s quiz to unlock chat.";

        // Persist in-app notifications
        saveNotification(actorId, "MATCH", title, actorBody, data);
        saveNotification(targetId, "MATCH", title, targetBody, data);

        // Notify actor via push
        sendPushToUser(actorId, title, actorBody, data);

        // Notify target via push
        sendPushToUser(targetId, title, targetBody, data);
    }

    /**
     * Dispatches notification for an incoming chat message.
     */
    public void sendChatMessageNotification(UUID recipientId, UUID matchId, String senderName, String messageSnippet) {
        Map<String, Object> data = Map.of(
                "type", "CHAT",
                "matchId", matchId.toString(),
                "url", "/chat/" + matchId
        );

        String title = senderName != null && !senderName.isBlank() ? senderName : "New Message";
        String body = messageSnippet != null && !messageSnippet.isBlank() ? messageSnippet : "Sent you a message";

        // Persist in-app notification
        saveNotification(recipientId, "CHAT", title, body, data);

        sendPushToUser(recipientId, title, body, data);
    }

    /**
     * Dispatches notification when 10-second icebreaker quiz unlocks the chat lounge.
     */
    public void sendChatUnlockedNotification(UUID recipientId, UUID matchId, String partnerName) {
        Map<String, Object> data = Map.of(
                "type", "CHAT_UNLOCKED",
                "matchId", matchId.toString(),
                "url", "/chat/" + matchId
        );

        String title = "Chat Lounge Unlocked! 💬";
        String body = partnerName + " completed the 10s icebreaker. Say hello!";

        // Persist in-app notification
        saveNotification(recipientId, "CHAT_UNLOCKED", title, body, data);

        sendPushToUser(recipientId, title, body, data);
    }

    /**
     * In-app Notification Inbox Queries and Actions
     */
    public List<NotificationDto.UserNotificationResponse> getUserNotifications(UUID userId, Boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        List<UserNotification> list;
        if (Boolean.TRUE.equals(unreadOnly)) {
            list = userNotificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false, pageable);
        } else {
            list = userNotificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return list.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public long getUnreadCount(UUID userId) {
        return userNotificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public boolean markAsRead(UUID id, UUID userId) {
        return userNotificationRepository.markAsRead(id, userId) > 0;
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        return userNotificationRepository.markAllAsRead(userId);
    }

    @Transactional
    public void deleteNotification(UUID id, UUID userId) {
        userNotificationRepository.deleteByIdAndUserId(id, userId);
    }

    private NotificationDto.UserNotificationResponse mapToDto(UserNotification n) {
        Map<String, Object> dataMap = Collections.emptyMap();
        if (n.getDataJson() != null && !n.getDataJson().isBlank()) {
            try {
                dataMap = objectMapper.readValue(n.getDataJson(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {}
        }
        return NotificationDto.UserNotificationResponse.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .data(dataMap)
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private int dispatchToExpo(List<Map<String, Object>> messages) {
        if (messages.isEmpty()) return 0;

        try {
            String jsonPayload = objectMapper.writeValueAsString(messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EXPO_PUSH_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Successfully dispatched {} push notifications to Expo Push Service (HTTP {})",
                        messages.size(), response.statusCode());
                return messages.size();
            } else {
                log.warn("Expo Push Service returned HTTP {}: {}", response.statusCode(), response.body());
                return 0;
            }
        } catch (Exception e) {
            log.warn("Failed to dispatch push notifications to Expo: {}", e.getMessage());
            return 0;
        }
    }
}
