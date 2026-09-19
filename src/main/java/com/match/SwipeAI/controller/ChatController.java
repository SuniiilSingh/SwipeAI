package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.ChatDto;
import com.match.SwipeAI.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Encrypted Chat Lounge and Shield 360 Moderation Controller.
 * Handles text/media messaging, Shield 360 nudity auto-blurring, and milestone karma tracking.
 */
@RestController
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * Fetch chronological chat messages for an active match.
     *
     * @param userId Authenticated user UUID
     * @param matchId Match UUID
     * @return List of message responses
     */
    @GetMapping("/{matchId}/messages")
    public ResponseEntity<List<ChatDto.ChatMessageResponse>> getMessages(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID matchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, Math.min(size, 200));
        List<ChatDto.ChatMessageResponse> messages = chatService.getMessages(matchId, userId, pageable);
        return ResponseEntity.ok(messages);
    }

    /**
     * Send a text or media message in the match.
     * Evaluates incoming media through Shield 360 AI Safe Detector for sensitive content auto-blurring.
     *
     * @param userId Authenticated sender UUID
     * @param matchId Match UUID
     * @param request Message content and media URL
     * @return Sent message response
     */
    @PostMapping("/{matchId}/messages")
    public ResponseEntity<ChatDto.ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID matchId,
            @RequestBody ChatDto.SendMessageRequest request) {
        ChatDto.ChatMessageResponse response = chatService.sendMessage(matchId, userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Mark all incoming messages in this match as read by the authenticated user.
     */
    @PostMapping("/{matchId}/read")
    public ResponseEntity<java.util.Map<String, String>> markMessagesAsRead(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID matchId) {
        chatService.markMessagesAsRead(matchId, userId);
        return ResponseEntity.ok(java.util.Map.of("status", "success", "message", "Messages marked as read."));
    }

    /**
     * Clear all messages in a chat match.
     */
    @DeleteMapping("/{matchId}/messages")
    public ResponseEntity<java.util.Map<String, String>> clearMessages(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID matchId) {
        chatService.clearMessages(matchId);
        return ResponseEntity.ok(java.util.Map.of("status", "success", "message", "Chat messages cleared successfully."));
    }
}
