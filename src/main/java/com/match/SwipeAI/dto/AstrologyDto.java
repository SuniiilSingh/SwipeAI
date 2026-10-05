package com.match.SwipeAI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

public class AstrologyDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAstrologyResponse {
        private UUID userId;
        private String birthTime;
        private String birthCity;
        private Boolean isExactTimeProvided;
        private Integer nakshatraId;
        private String nakshatraName;
        private Integer nakshatraPada;
        private String chandraRashi;
        private String chandraRashiLord;
        private String sunSign;
        private String lagnaSign;
        private String varna;
        private String vashya;
        private String yoniAnimal;
        private String gana;
        private String nadi;
        private Integer numerologyNumber;
        private Boolean isManglik;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateBirthDetailsRequest {
        private String birthTime; // "HH:mm" e.g. "15:45"
        private String birthCity; // e.g. "Jaipur"
        private Double birthLat;
        private Double birthLng;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AshtakootMatchResponse {
        private Integer totalScore; // out of 36
        private Integer maxScore; // 36
        private Integer percentage; // e.g. 86
        private String vibeTitle; // e.g. "Celestial Soul Alignment ✨"
        private String vibeSummary; // modern relationship summary
        
        // 8 Guna Breakdown
        private Integer varnaScore; // Max 1
        private String varnaDescription;

        private Integer vashyaScore; // Max 2
        private String vashyaDescription;

        private Double taraScore; // Max 3
        private String taraDescription;

        private Integer yoniScore; // Max 4
        private String yoniDescription;

        private Integer grahaMaitriScore; // Max 5
        private String grahaMaitriDescription;

        private Integer ganaScore; // Max 6
        private String ganaDescription;

        private Integer bhakootScore; // Max 7
        private String bhakootDescription;

        private Integer nadiScore; // Max 8
        private String nadiDescription;

        private Boolean isManglikCompatible;
        private String manglikSummary;

        private AstroSummary viewer;
        private AstroSummary candidate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AstroSummary {
        private String displayName;
        private String sunSign;
        private String chandraRashi;
        private String nakshatraName;
        private Integer nakshatraPada;
        private String yoniAnimal;
        private String gana;
        private String nadi;
        private Integer numerologyNumber;
        private Boolean isManglik;
        private Boolean isExactTime;
    }
}
