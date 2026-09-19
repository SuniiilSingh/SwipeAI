package com.match.SwipeAI.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class DesireDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DesireProfileRequest {
        private Integer minAge;
        private Integer maxAge;
        private Boolean ageFlexible;
        private Integer maxDistanceKm;
        private String dietaryHarmony;
        private String smokingComfort;
        private String drinkingComfort;
        private String livingSituationComfort;
        private String relationshipIntentMatch;
        private String weekendVibe;
        private String communicationPace;
        private String banterStyle;
        private String loveLanguage;
        private List<String> greenFlags;
        private List<String> preferredProfessions;
        private String naturalLanguagePrompt;
        private Boolean isConfigured;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DesireProfileResponse {
        private UUID userId;
        private Boolean isConfigured;
        private Integer minAge;
        private Integer maxAge;
        private Boolean ageFlexible;
        private Integer maxDistanceKm;
        private String dietaryHarmony;
        private String smokingComfort;
        private String drinkingComfort;
        private String livingSituationComfort;
        private String relationshipIntentMatch;
        private String weekendVibe;
        private String communicationPace;
        private String banterStyle;
        private String loveLanguage;
        private List<String> greenFlags;
        private List<String> preferredProfessions;
        private String naturalLanguagePrompt;
        private OffsetDateTime updatedAt;
    }
}
