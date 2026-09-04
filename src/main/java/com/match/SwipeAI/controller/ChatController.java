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
            @PathVariable UUID matchId) {
        List<ChatDto.ChatMessageResponse> messages = chatService.getMessages(matchId, userId);
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
