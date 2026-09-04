package com.match.SwipeAI.dto;

import com.match.SwipeAI.enums.ActionType;
import com.match.SwipeAI.enums.ContextType;
import com.match.SwipeAI.enums.DietaryPreference;
import com.match.SwipeAI.enums.LivingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Objects for High-Intent Discovery Feed and Micro-Circle Filtering.
 */
public class DiscoveryDto {

    /**
     * Request payload to fetch candidate cards from the recommendation engine.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscoveryFeedRequest {
        private Double latitude;
        private Double longitude;
        private Double maxDistanceKm;
        private List<DietaryPreference> dietaryFilters;
        private String intentFilter;
        private String microCircle;
        private Integer limit = 10;
    }

    /**
     * Feed response containing anti-fatigue daily swipe counter and ranked candidates.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscoveryFeedResponse {
        private String status;
        private FeedData data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedData {
        private int remainingDailySwipes;
        private int dailyHardCap;
        private List<CandidateCardDto> candidates;
    }

    /**
     * Rich candidate profile card.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateCardDto {
        private UUID userId;
        private String displayName;
        private int age;
        private boolean isDigilockerVerified;
        private boolean isWhatsappVerified;
        private double livenessScore;
        private double distanceKm;
        private CulturalBadges culturalBadges;
        private VoicePromptDto voicePrompt;
        private MemeMatchDto memeMatch;
        private CosmicChemistryDto cosmicChemistry;
        private int compatibilityScore;
        private String bio;
        private String company;
        private String occupation;
        private String city;
        private String neighborhood;
        private String microCircle;
        private List<String> photos;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CulturalBadges {
        private DietaryPreference diet;
        private LivingStatus living;
        private List<String> languages;
        private String zodiac;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VoicePromptDto {
        private String audioUrl;
        private int durationSec;
        private String promptText;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemeMatchDto {
        private int matchPercent;
        private String memeImageUrl;
        private String memeTitle;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CosmicChemistryDto {
        private String synergyTag;
        private int score;
    }

    /**
     * High-intent contextual interaction request.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionRequest {
        private UUID targetId;
        private ActionType actionType;
        private ContextType contextType;
        private String contextTargetId;
        private String commentText;
    }

    /**
     * Interaction response indicating if a mutual match was formed.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionResponse {
        private boolean isMatch;
        private UUID matchId;
        private String message;
        private int remainingDailySwipes;
    }

    /**
     * Micro-community circle details.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CircleDto {
        private String id;
        private String name;
        private String description;
        private int activeMembers;
        private String icon;
    }
}
