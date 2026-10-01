package com.match.SwipeAI.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Granular dietary preferences tailored for Indian dating dynamics.
 * Heavily weighted (0.40) in the Cultural Overlap multi-objective matching formula:
 * M_cultural = 0.40 * DietMatch + 0.30 * LanguageOverlap + 0.20 * LivingConditionFit + 0.10 * CosmicVibeScore
 */
public enum DietaryPreference {
    /**
     * Strict Jain: No root vegetables (onion/garlic/potatoes), pure vegetarian.
     */
    STRICT_JAIN,

    /**
     * Pure Vegetarian: Lacto-vegetarian, zero meat/eggs/fish.
     */
    PURE_VEG,

    /**
     * Vegan: Plant-based, zero animal/dairy products.
     */
    VEGAN,

    /**
     * Eggetarian: Vegetarian who consumes eggs.
     */
    EGGETARIAN,

    /**
     * Non-Vegetarian.
     */
    NON_VEG;

    @JsonCreator
    public static DietaryPreference fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        if (normalized.contains("JAIN")) return STRICT_JAIN;
        if (normalized.contains("VEGAN")) return VEGAN;
        if (normalized.contains("EGG")) return EGGETARIAN;
        if (normalized.contains("NON")) return NON_VEG;
        if (normalized.contains("PURE") || (normalized.contains("VEG") && !normalized.contains("NON") && !normalized.contains("EGG"))) return PURE_VEG;
        try {
            return DietaryPreference.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
