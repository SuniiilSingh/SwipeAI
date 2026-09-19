package com.match.SwipeAI.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.SwipeAI.model.PushToken;
import com.match.SwipeAI.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private final PushTokenRepository pushTokenRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

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

        // Notify actor
        sendPushToUser(
                actorId,
                "It's a Match! ✨",
                "You and " + targetName + " liked each other! Answer the 10s quiz to unlock chat.",
                data
        );

        // Notify target
        sendPushToUser(
                targetId,
                "It's a Match! ✨",
                "You and " + actorName + " liked each other! Answer the 10s quiz to unlock chat.",
                data
        );
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

        sendPushToUser(
                recipientId,
                "Chat Lounge Unlocked! 💬",
                partnerName + " completed the 10s icebreaker. Say hello!",
                data
        );
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
