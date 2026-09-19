package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.NotificationDto;
import com.match.SwipeAI.service.integration.PushNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final PushNotificationService pushNotificationService;

    /**
     * Register or update an Expo push token for the authenticated user.
     */
    @PostMapping("/push-token")
    public ResponseEntity<NotificationDto.PushResponse> registerPushToken(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody NotificationDto.RegisterTokenRequest request) {
        log.info("Registering push token for user {}: {}", userId, request.getToken());
        pushNotificationService.registerToken(userId, request.getToken(), request.getPlatform());
        return ResponseEntity.ok(NotificationDto.PushResponse.builder()
                .status("success")
                .message("Push token successfully registered.")
                .dispatchedCount(1)
                .build());
    }

    /**
     * Unregister a push token (e.g. upon user logout).
     */
    @DeleteMapping("/push-token")
    public ResponseEntity<NotificationDto.PushResponse> unregisterPushToken(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody NotificationDto.UnregisterTokenRequest request) {
        log.info("Unregistering push token for user {}: {}", userId, request.getToken());
        pushNotificationService.unregisterToken(userId, request.getToken());
        return ResponseEntity.ok(NotificationDto.PushResponse.builder()
                .status("success")
                .message("Push token successfully unregistered.")
                .build());
    }

    /**
     * Trigger a test push notification to all devices registered to the current user.
     */
    @PostMapping("/test-push")
    public ResponseEntity<NotificationDto.PushResponse> sendTestPush(
            @AuthenticationPrincipal UUID userId,
            @RequestBody(required = false) NotificationDto.TestPushRequest request) {
        String title = (request != null && request.getTitle() != null) ? request.getTitle() : "Match Alert ✨";
        String body = (request != null && request.getBody() != null) ? request.getBody() : "Someone exciting liked your profile on Blunderr Dating!";
        Map<String, Object> data = (request != null && request.getData() != null) ? request.getData() : Map.of("type", "TEST");

        log.info("Dispatching test push to user {}: {} - {}", userId, title, body);
        int dispatched = pushNotificationService.sendPushToUser(userId, title, body, data).join();

        return ResponseEntity.ok(NotificationDto.PushResponse.builder()
                .status("success")
                .message("Test notification dispatched.")
                .dispatchedCount(dispatched)
                .build());
    }
}
