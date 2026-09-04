package com.match.SwipeAI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

public class KycDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DigiLockerInitiateResponse {
        private String authUrl;
        private String stateToken;
        private String message;
        private boolean simulated;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DigiLockerProofRequest {
        private String stateToken;
        private String code;
        private boolean simulateSuccess = true;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DigiLockerProofResponse {
        private boolean isVerified;
        private boolean isAdult;
        private String gender;
        private String maskedCity;
        private String badge; // "GOLD_SHIELD"
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LivenessRequest {
        private String selfieFrameBase64;
        private int headTurnDurationMs;
        private boolean simulatePass = true;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LivenessResponse {
        private boolean isLiveHuman;
        private double livenessScore;
        private String message;
    }
}
