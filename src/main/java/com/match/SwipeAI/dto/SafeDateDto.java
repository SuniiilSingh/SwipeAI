package com.match.SwipeAI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

public class SafeDateDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SafeDateSpotDto {
        private Long id;
        private String name;
        private String brand;
        private String address;
        private String city;
        private String neighborhood;
        private Double latitude;
        private Double longitude;
        private int discountPercent;
        private String couponCode;
        private boolean sosEnabled;
        private String photoUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SosStartRequest {
        private Long safeSpotId;
        private UUID matchId;
        private List<String> emergencyContacts;
        private String meetingNotes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SosStartResponse {
        private String trackingSessionId;
        private String trackingUrl; // Dynamic link shared with emergency contacts
        private String cafeName;
        private String discountCoupon;
        private String status; // ACTIVE, ALERT
        private String message;
    }
}
