package com.match.SwipeAI.service;

import com.match.SwipeAI.dto.DesireDto;
import com.match.SwipeAI.enums.DietaryPreference;
import com.match.SwipeAI.model.DesireProfile;
import com.match.SwipeAI.model.Profile;
import com.match.SwipeAI.repository.DesireProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DesireProfileService {

    private final DesireProfileRepository desireProfileRepository;

    @Transactional(readOnly = true)
    public DesireDto.DesireProfileResponse getDesireProfile(UUID userId) {
        return desireProfileRepository.findById(userId)
                .map(this::toResponse)
                .orElseGet(() -> toResponse(buildDefaultDesireProfile(userId, false)));
    }

    @Transactional
    public DesireDto.DesireProfileResponse saveDesireProfile(UUID userId, DesireDto.DesireProfileRequest request) {
        DesireProfile desire = desireProfileRepository.findById(userId)
                .orElseGet(() -> DesireProfile.builder().userId(userId).build());

        desire.setIsConfigured(true);
        if (request.getMinAge() != null) desire.setMinAge(request.getMinAge());
        if (request.getMaxAge() != null) desire.setMaxAge(request.getMaxAge());
        if (request.getAgeFlexible() != null) desire.setAgeFlexible(request.getAgeFlexible());
        if (request.getMaxDistanceKm() != null) desire.setMaxDistanceKm(request.getMaxDistanceKm());
        if (request.getDietaryHarmony() != null) desire.setDietaryHarmony(request.getDietaryHarmony());
        if (request.getSmokingComfort() != null) desire.setSmokingComfort(request.getSmokingComfort());
        if (request.getDrinkingComfort() != null) desire.setDrinkingComfort(request.getDrinkingComfort());
        if (request.getLivingSituationComfort() != null) desire.setLivingSituationComfort(request.getLivingSituationComfort());
        if (request.getRelationshipIntentMatch() != null) desire.setRelationshipIntentMatch(request.getRelationshipIntentMatch());
        if (request.getWeekendVibe() != null) desire.setWeekendVibe(request.getWeekendVibe());
        if (request.getCommunicationPace() != null) desire.setCommunicationPace(request.getCommunicationPace());
        if (request.getBanterStyle() != null) desire.setBanterStyle(request.getBanterStyle());
        if (request.getLoveLanguage() != null) desire.setLoveLanguage(request.getLoveLanguage());
        if (request.getGreenFlags() != null) desire.setGreenFlags(new ArrayList<>(request.getGreenFlags()));
        if (request.getPreferredProfessions() != null) desire.setPreferredProfessions(new ArrayList<>(request.getPreferredProfessions()));
        if (request.getNaturalLanguagePrompt() != null) desire.setNaturalLanguagePrompt(request.getNaturalLanguagePrompt());

        DesireProfile saved = desireProfileRepository.save(desire);
        log.info("Saved and activated DesireProfile for user {}", userId);
        return toResponse(saved);
    }

    public DesireProfile buildDefaultDesireProfile(UUID userId, boolean isConfigured) {
        return DesireProfile.builder()
                .userId(userId)
                .isConfigured(isConfigured)
                .minAge(21)
                .maxAge(34)
                .ageFlexible(true)
                .maxDistanceKm(50)
                .dietaryHarmony("ANY_DIET")
                .smokingComfort("NON_SMOKER_PREFERRED")
                .drinkingComfort("SOCIAL_DRINKER_OK")
                .livingSituationComfort("NO_PREFERENCE")
                .relationshipIntentMatch("ANY")
                .weekendVibe("COFFEE_AND_BOOKS")
                .communicationPace("VOICE_NOTES_AND_MEMES")
                .banterStyle("DRY_WIT")
                .loveLanguage("QUALITY_TIME")
                .greenFlags(List.of("Reads physical books 📚", "Emotionally articulate 🧠", "Orders dessert for the table 🍰"))
                .preferredProfessions(List.of())
                .naturalLanguagePrompt("Someone authentic and creative who enjoys good coffee, meaningful conversations, and exploring the city.")
                .build();
    }

    public DesireMatchResult calculateDesireMatch(DesireProfile desire, Profile candidateProfile, int candidateAge, double distanceKm) {
        if (desire == null) {
            return new DesireMatchResult(85, List.of("High vibe alignment"));
        }

        double score = 45.0;
        List<String> highlights = new ArrayList<>();

        // 1. Age Fit (up to 15 pts)
        int minAge = desire.getMinAge() != null ? desire.getMinAge() : 20;
        int maxAge = desire.getMaxAge() != null ? desire.getMaxAge() : 38;
        if (candidateAge >= minAge && candidateAge <= maxAge) {
            score += 15.0;
            highlights.add(String.format("Ideal age range (%d-%d)", minAge, maxAge));
        } else if (Boolean.TRUE.equals(desire.getAgeFlexible()) && candidateAge >= (minAge - 2) && candidateAge <= (maxAge + 2)) {
            score += 10.0;
        }

        // 2. Distance Fit (up to 10 pts)
        int maxDist = desire.getMaxDistanceKm() != null ? desire.getMaxDistanceKm() : 50;
        if (distanceKm <= maxDist) {
            score += 10.0;
            if (distanceKm <= 10.0) {
                highlights.add(distanceKm <= 3.0 ? "Within 3 km of you 📍" : "Within 10 km of you 📍");
            }
        }

        // 3. Dietary Harmony (up to 15 pts)
        if (candidateProfile != null && candidateProfile.getDietaryPref() != null) {
            DietaryPreference cDiet = candidateProfile.getDietaryPref();
            String desireDiet = desire.getDietaryHarmony() != null ? desire.getDietaryHarmony() : "ANY_DIET";

            if ("ANY_DIET".equalsIgnoreCase(desireDiet)) {
                score += 15.0;
            } else if ("VEG_SPECTRUM".equalsIgnoreCase(desireDiet)) {
                if (cDiet == DietaryPreference.PURE_VEG || cDiet == DietaryPreference.STRICT_JAIN || cDiet == DietaryPreference.VEGAN) {
                    score += 15.0;
                    highlights.add("100% Vegetarian match 🥗");
                } else {
                    score += 3.0;
                }
            } else if ("STRICT_JAIN_ONLY".equalsIgnoreCase(desireDiet)) {
                if (cDiet == DietaryPreference.STRICT_JAIN) {
                    score += 15.0;
                    highlights.add("Strict Jain compatibility 🪷");
                }
            } else if ("EGGETARIAN_OR_VEG".equalsIgnoreCase(desireDiet)) {
                if (cDiet != DietaryPreference.NON_VEG) {
                    score += 15.0;
                    highlights.add("Dietary harmony 🍳");
                }
            } else {
                score += 12.0;
            }
        } else {
            score += 10.0;
        }

        // 4. Smoking & Drinking Habits (up to 10 pts)
        if (candidateProfile != null) {
            String smoke = candidateProfile.getSmokingHabit() != null ? candidateProfile.getSmokingHabit().toLowerCase() : "";
            if ("NON_SMOKER_PREFERRED".equalsIgnoreCase(desire.getSmokingComfort())) {
                if (smoke.contains("non") || smoke.contains("never") || smoke.contains("quit") || smoke.isBlank()) {
                    score += 5.0;
                    highlights.add("Non-smoker match 🚭");
                }
            } else {
                score += 5.0;
            }

            String drink = candidateProfile.getDrinkingHabit() != null ? candidateProfile.getDrinkingHabit().toLowerCase() : "";
            if ("TEETOTALER_PREFERRED".equalsIgnoreCase(desire.getDrinkingComfort())) {
                if (drink.contains("non") || drink.contains("sober") || drink.contains("teetotaler")) {
                    score += 5.0;
                    highlights.add("Teetotaler match 🚫🍺");
                }
            } else {
                score += 5.0;
            }
        }

        // 5. Weekend Vibe Compatibility (up to 10 pts)
        if (candidateProfile != null && candidateProfile.getVacationPreference() != null) {
            String vac = candidateProfile.getVacationPreference().toLowerCase();
            String vibe = desire.getWeekendVibe() != null ? desire.getWeekendVibe().toLowerCase() : "";
            if (vibe.contains("mountain") || vibe.contains("trek")) {
                if (vac.contains("mountain") || vac.contains("both")) {
                    score += 10.0;
                    highlights.add("Shared mountain & trek vibe ⛰️");
                } else {
                    score += 5.0;
                }
            } else if (vibe.contains("beach")) {
                if (vac.contains("beach") || vac.contains("both")) {
                    score += 10.0;
                    highlights.add("Shared beach & getaway vibe 🏖️");
                } else {
                    score += 5.0;
                }
            } else {
                score += 8.0;
                highlights.add("Weekend vibe match ☕");
            }
        }

        // 6. Green Flags & Persona Resonance (up to 10 pts)
        if (candidateProfile != null && desire.getGreenFlags() != null && !desire.getGreenFlags().isEmpty()) {
            String combinedCandidateText = ((candidateProfile.getBio() != null ? candidateProfile.getBio() : "") + " " +
                    (candidateProfile.getInterests() != null ? candidateProfile.getInterests() : "") + " " +
                    (candidateProfile.getHobbies() != null ? candidateProfile.getHobbies() : "") + " " +
                    (candidateProfile.getVoicePromptText() != null ? candidateProfile.getVoicePromptText() : "")).toLowerCase();

            for (String flag : desire.getGreenFlags()) {
                String cleanFlag = flag.replaceAll("[^a-zA-Z0-9 ]", "").trim().toLowerCase();
                if (!cleanFlag.isEmpty() && combinedCandidateText.contains(cleanFlag)) {
                    score += 5.0;
                    highlights.add("Green flag match: " + flag);
                    break;
                }
            }
        }

        // 7. Preferred Professions Resonance (up to 10 pts)
        if (candidateProfile != null && desire.getPreferredProfessions() != null && !desire.getPreferredProfessions().isEmpty()) {
            String candidateJob = ((candidateProfile.getJob() != null ? candidateProfile.getJob() : "") + " " +
                    (candidateProfile.getOccupation() != null ? candidateProfile.getOccupation() : "")).toLowerCase();

            for (String prof : desire.getPreferredProfessions()) {
                String cleanProf = prof.replaceAll("[^a-zA-Z0-9 ]", "").trim().toLowerCase();
                if (!cleanProf.isEmpty() && (candidateJob.contains(cleanProf) || cleanProf.contains(candidateJob.trim()))) {
                    score += 8.0;
                    highlights.add("Career alignment: " + prof + " 💼");
                    break;
                }
            }
        }

        int finalScore = Math.min(99, Math.max(50, (int) Math.round(score)));
        if (highlights.isEmpty()) {
            highlights.add("High lifestyle synergy ✨");
        }
        return new DesireMatchResult(finalScore, highlights.subList(0, Math.min(3, highlights.size())));
    }

    public record DesireMatchResult(int scorePercent, List<String> highlights) {}

    public DesireDto.DesireProfileResponse toResponse(DesireProfile entity) {
        return DesireDto.DesireProfileResponse.builder()
                .userId(entity.getUserId())
                .isConfigured(entity.getIsConfigured() != null ? entity.getIsConfigured() : false)
                .minAge(entity.getMinAge())
                .maxAge(entity.getMaxAge())
                .ageFlexible(entity.getAgeFlexible())
                .maxDistanceKm(entity.getMaxDistanceKm())
                .dietaryHarmony(entity.getDietaryHarmony())
                .smokingComfort(entity.getSmokingComfort())
                .drinkingComfort(entity.getDrinkingComfort())
                .livingSituationComfort(entity.getLivingSituationComfort())
                .relationshipIntentMatch(entity.getRelationshipIntentMatch())
                .weekendVibe(entity.getWeekendVibe())
                .communicationPace(entity.getCommunicationPace())
                .banterStyle(entity.getBanterStyle())
                .loveLanguage(entity.getLoveLanguage())
                .greenFlags(entity.getGreenFlags() != null ? new ArrayList<>(entity.getGreenFlags()) : List.of())
                .preferredProfessions(entity.getPreferredProfessions() != null ? new ArrayList<>(entity.getPreferredProfessions()) : List.of())
                .naturalLanguagePrompt(entity.getNaturalLanguagePrompt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
