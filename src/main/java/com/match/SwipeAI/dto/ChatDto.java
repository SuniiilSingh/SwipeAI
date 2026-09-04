package com.match.SwipeAI.dto;

import com.match.SwipeAI.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Data Transfer Objects for Encrypted Chat Lounge and Shield 360 Moderation.
 */
public class ChatDto {

    /**
     * Request payload to send a message inside an active match.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendMessageRequest {
        private String content;
        private String mediaUrl;
        private MessageType mediaType = MessageType.TEXT;
    }

    /**
     * Chat message response payload.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessageResponse {
        private UUID id;
        private UUID matchId;
        private UUID senderId;
        private UUID recipientId;
        private String content;
        private String mediaUrl;
        private MessageType mediaType;
        private boolean isBlurred;
        private String blurReason;
        private OffsetDateTime createdAt;
        private boolean isFromMe;
    }

    /**
     * Shield 360 AI Safe Detector scan result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NudityCheckResponse {
        private boolean isSensitive;
        private double confidenceScore;
        private String action;
        private String message;
    }
}
