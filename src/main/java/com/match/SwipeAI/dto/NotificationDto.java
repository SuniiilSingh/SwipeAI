package com.match.SwipeAI.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

public class NotificationDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterTokenRequest {
        @NotBlank(message = "Push token cannot be empty")
        private String token;

        @Builder.Default
        private String platform = "ANDROID";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnregisterTokenRequest {
        @NotBlank(message = "Push token cannot be empty")
        private String token;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestPushRequest {
        @Builder.Default
        private String title = "Match Alert ✨";

        @Builder.Default
        private String body = "Someone exciting liked your profile on Blunderr Dating!";

        private Map<String, Object> data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PushResponse {
        private String status;
        private String message;
        private Integer dispatchedCount;
    }
}
