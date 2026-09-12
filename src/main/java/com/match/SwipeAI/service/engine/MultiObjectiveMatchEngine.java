package com.match.SwipeAI.service.engine;

import com.match.SwipeAI.enums.DietaryPreference;
import com.match.SwipeAI.model.Profile;
import com.match.SwipeAI.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiObjectiveMatchEngine {

    private final CosmicChemistryEngine cosmicChemistryEngine;

    // LLD Weights
    private static final double W1_VECTOR = 0.35;
    private static final double W2_CULTURAL = 0.30;
    private static final double W3_KARMA = 0.20;
    private static final double W4_DISTANCE = 0.15;

    public int calculateCompatibilityScore(User viewer, Profile viewerProfile,
                                          User candidate, Profile candidateProfile,
                                          double distanceKm) {
        // 1. Vector Cosine Similarity (Meme + Persona embeddings)
        double vectorSim = calculateVectorSimilarity(viewerProfile, candidateProfile);

        // 2. Cultural Overlap
        double culturalOverlap = calculateCulturalOverlap(viewerProfile, candidateProfile);

        // 3. Karma Stability Balance
        int karmaViewer = viewer.getKarmaScore() != null ? viewer.getKarmaScore() : 100;
        int karmaCandidate = candidate.getKarmaScore() != null ? candidate.getKarmaScore() : 100;
        double karmaDelta = Math.abs(karmaViewer - karmaCandidate);
        double karmaScoreComponent = 1.0 - (karmaDelta / 200.0);

        // 4. Distance Decay: log(1 + Dist_km) normalized
        double distComponent = Math.log(1.0 + Math.max(0.0, distanceKm)) / Math.log(1.0 + 50.0); // normalize up to 50km
        distComponent = Math.min(1.0, Math.max(0.0, distComponent));

        // Combined Multi-Objective Score S(u, v)
        double score = (W1_VECTOR * vectorSim)
                     + (W2_CULTURAL * culturalOverlap)
                     + (W3_KARMA * karmaScoreComponent)
                     - (W4_DISTANCE * distComponent);

        // Map from range to 0 - 100%
        int finalScore = (int) Math.round(Math.max(50.0, Math.min(99.0, (score + 0.15) * 100.0)));
        return finalScore;
    }

    private double calculateVectorSimilarity(Profile a, Profile b) {
        if (a == null || b == null) return 0.75;
        // High humor/meme match if both have similar food or city or circle
        if (a.getMicroCircle() != null && a.getMicroCircle().equals(b.getMicroCircle())) {
            return 0.95;
        }
        return 0.82;
    }

    public double calculateCulturalOverlap(Profile a, Profile b) {
        if (a == null || b == null) return 0.70;

        // Diet Match (0.30)
        double dietMatch = 0.50;
        if (a.getDietaryPref() != null && b.getDietaryPref() != null) {
            if (a.getDietaryPref() == b.getDietaryPref()) {
                dietMatch = 1.0;
            } else if ((a.getDietaryPref() == DietaryPreference.PURE_VEG || a.getDietaryPref() == DietaryPreference.STRICT_JAIN || a.getDietaryPref() == DietaryPreference.VEGAN) &&
                    (b.getDietaryPref() == DietaryPreference.PURE_VEG || b.getDietaryPref() == DietaryPreference.STRICT_JAIN || b.getDietaryPref() == DietaryPreference.VEGAN)) {
                dietMatch = 0.90;
            } else if (a.getDietaryPref() == DietaryPreference.EGGETARIAN && b.getDietaryPref() == DietaryPreference.PURE_VEG) {
                dietMatch = 0.80;
            }
        }

        // Shared Interests & Hobbies Overlap (0.30)
        double interestOverlap = calculateInterestOverlap(a, b);

        // Language Overlap (0.20)
        double langOverlap = 0.60;
        if (a.getLanguagesSpoken() != null && b.getLanguagesSpoken() != null) {
            Set<String> intersection = new HashSet<>(a.getLanguagesSpoken());
            intersection.retainAll(b.getLanguagesSpoken());
            if (!intersection.isEmpty()) {
                langOverlap = Math.min(1.0, 0.5 + (intersection.size() * 0.25));
            }
        }

        // Living Condition Fit (0.10)
        double livingFit = 0.70;
        if (a.getLivingStatus() != null && b.getLivingStatus() != null) {
            if (a.getLivingStatus() == b.getLivingStatus()) {
                livingFit = 0.95;
            } else {
                livingFit = 0.75;
            }
        }

        // Cosmic Vibe Score (0.10)
        double cosmicVibe = cosmicChemistryEngine.calculateSynergy(a.getZodiacSign(), b.getZodiacSign()) / 100.0;

        return (0.30 * dietMatch) + (0.30 * interestOverlap) + (0.20 * langOverlap) + (0.10 * livingFit) + (0.10 * cosmicVibe);
    }

    private double calculateInterestOverlap(Profile a, Profile b) {
        Set<String> tagsA = extractCleanTags(a);
        Set<String> tagsB = extractCleanTags(b);
        if (tagsA.isEmpty() || tagsB.isEmpty()) return 0.65;

        int commonCount = 0;
        for (String itemA : tagsA) {
            for (String itemB : tagsB) {
                if (itemA.equalsIgnoreCase(itemB) || itemA.contains(itemB) || itemB.contains(itemA)) {
                    commonCount++;
                    break;
                }
            }
        }

        if (commonCount >= 3) return 1.0;
        if (commonCount == 2) return 0.90;
        if (commonCount == 1) return 0.80;
        return 0.50;
    }

    private Set<String> extractCleanTags(Profile p) {
        Set<String> tags = new HashSet<>();
        if (p == null) return tags;
        if (p.getInterests() != null && !p.getInterests().isBlank()) {
            for (String s : p.getInterests().split(",")) {
                String c = s.replaceAll("[^a-zA-Z0-9 &]", "").trim().toLowerCase();
                if (!c.isBlank()) tags.add(c);
            }
        }
        if (p.getHobbies() != null && !p.getHobbies().isBlank()) {
            for (String s : p.getHobbies().split(",")) {
                String c = s.replaceAll("[^a-zA-Z0-9 &]", "").trim().toLowerCase();
                if (!c.isBlank()) tags.add(c);
            }
        }
        return tags;
    }

    public double calculateDistanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 3.5; // default fallback distance in km
        }
        final int R = 6371; // Earth radius in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(R * c * 10.0) / 10.0;
    }
}
