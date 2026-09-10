package com.match.SwipeAI.dto;

import com.match.SwipeAI.enums.DatingIntent;
import com.match.SwipeAI.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Data Transfer Objects for Authentication and Session Onboarding.
 */
public class AuthDto {

    /**
     * Request payload to initiate mobile / WhatsApp OTP dispatch.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendOtpRequest {
        @NotBlank(message = "Phone number is required in E.164 format")
        private String phoneE164;
        private String channel; // "sms" or "whatsapp"
    }

    /**
     * Request payload to verify 4-digit OTP and complete login / registration.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifyOtpRequest {
        @NotBlank(message = "Phone number is required")
        private String phoneE164;

        @NotBlank(message = "OTP code is required")
        private String otp;

        private String channel; // "sms" or "whatsapp"
        private Gender gender;
        private DatingIntent intent;
        private LocalDate birthDate;
    }

    /**
     * Request payload for WhatsApp 1-tap deep-link authentication.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WhatsAppLoginRequest {
        @NotBlank(message = "Phone number is required")
        private String phoneE164;

        private String authCode;
        private String otp;
        private String channel = "whatsapp";
        private Gender gender;
        private DatingIntent intent;
        private LocalDate birthDate;
    }

    /**
     * Successful authentication response containing JWT Bearer token and user badges.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        private String token;
        private UUID userId;
        private String phoneE164;
        private boolean isNewUser;
        private boolean digilockerVerified;
        private boolean whatsappVerified;
        private double livenessScore;
        private int karmaScore;
        private int sparksBalance;
        private boolean hasActivePass;
    }
}
