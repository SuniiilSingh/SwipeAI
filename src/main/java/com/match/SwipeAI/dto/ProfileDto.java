package com.match.SwipeAI.dto;

import com.match.SwipeAI.enums.DatingIntent;
import com.match.SwipeAI.enums.DietaryPreference;
import com.match.SwipeAI.enums.Gender;
import com.match.SwipeAI.enums.LivingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Objects for Profile attributes, Cultural Canvas, and Full MatchAI Edit Profile features.
 */
public class ProfileDto {

    /**
     * Request payload to update full profile attributes and onboarding details.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileRequest {
        private String displayName;
        private String fullName;
        private String bio;
        private DietaryPreference dietaryPref;
        private LivingStatus livingStatus;
        private List<String> languagesSpoken;
        private String zodiacSign;
        private String sunSign;
        private String moonSign;
        private String company;
        private String occupation;
        private String job;
        private String education;
        private String interests;
        private Integer height;
        private String location;
        private Integer maxDistanceKm;
        private String sexualOrientation;
        private Boolean showOrientationOnProfile;
        private String genderDisplay;
        private Boolean showGenderOnProfile;
        private String genderPreferenceDisplay;
        private String relationshipIntent;
        private String profilePromptQuestion;
        private String profilePromptAnswer;
        private String photo1;
        private String photo2;
        private String photo3;
        private String photo4;
        private String photo5;
        private String photo6;
        private String selfieUrl;
        private String smokingHabit;
        private String drinkingHabit;
        private String hobbies;
        private String vacationPreference;
        private String city;
        private String neighborhood;
        private String microCircle;
        private List<String> photos;
        private DatingIntent intent;
        private Gender gender;
        private LocalDate birthDate;
        private Double latitude;
        private Double longitude;
        private String voicePromptUrl;
        private Integer voicePromptDuration;
        private String voicePromptText;
        private String selectedMemeUrl;
        private String selectedMemeTitle;
    }

    /**
     * Request payload to update only the user's GPS coordinates.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationUpdateRequest {
        private Double latitude;
        private Double longitude;
    }

    /**
     * Comprehensive user profile response with cultural badges, completion score, and micro-transaction balances.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileResponse {
        private UUID userId;
        private String phoneE164;
        private String displayName;
        private String fullName;
        private String bio;
        private int age;
        private LocalDate birthDate;
        private Gender gender;
        private DatingIntent intent;
        private boolean digilockerVerified;
        private boolean whatsappVerified;
        private double livenessScore;
        private int karmaScore;
        private DietaryPreference dietaryPref;
        private LivingStatus livingStatus;
        private List<String> languagesSpoken;
        private String zodiacSign;
        private String sunSign;
        private String moonSign;
        private String voicePromptUrl;
        private Integer voicePromptDuration;
        private String voicePromptText;
        private String company;
        private String occupation;
        private String job;
        private String education;
        private String interests;
        private Integer height;
        private String location;
        private Integer maxDistanceKm;
        private Double latitude;
        private Double longitude;
        private String sexualOrientation;
        private Boolean showOrientationOnProfile;
        private String genderDisplay;
        private Boolean showGenderOnProfile;
        private String genderPreferenceDisplay;
        private String relationshipIntent;
        private String profilePromptQuestion;
        private String profilePromptAnswer;
        private String photo1;
        private String photo2;
        private String photo3;
        private String photo4;
        private String photo5;
        private String photo6;
        private String selfieUrl;
        private String smokingHabit;
        private String drinkingHabit;
        private String hobbies;
        private String vacationPreference;
        private String city;
        private String neighborhood;
        private String microCircle;
        private List<String> photos;
        private int completionPercentage;
        private int memeCount;
        private int sparksBalance;
        private int boostsBalance;
        private int directDmsBalance;
        private boolean hasActivePass;
        private String selectedMemeUrl;
        private String selectedMemeTitle;
    }

    /**
     * Request payload for uploading a vernacular voice bio snippet.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VoicePromptUploadRequest {
        private String voicePromptUrl;
        private int durationSec;
        private String promptText;
    }

    /**
     * Request payload for daily Meme DNA swiping mini-game.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemeSwipeRequest {
        private String memeId;
        private boolean liked;
    }

    /**
     * Shareable Instagram-ready Cosmic Chemistry 2.0 vibe card.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CosmicChemistryResponse {
        private String viewerSign;
        private String candidateSign;
        private int overallSynergyScore;
        private String headline;
        private String vibeReport;
        private List<String> sharedStrengths;
        private String conversationalSpark;
    }
}
