package com.match.SwipeAI.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Declared relationship intent chosen by the user during onboarding.
 * Enforces Algorithmic Separation: Users with conflicting intents (e.g. Marriage vs Casual)
 * are never surfaced in each other's primary discovery feed to curb ghosting and mismatched expectations.
 */
public enum DatingIntent {
    /**
     * Marriage-minded users looking for high-intent, long-term matrimony alignment.
     */
    MARRIAGE_MINDED,

    /**
     * Users seeking committed, serious romantic relationships.
     */
    SERIOUS_DATING,

    /**
     * Users looking for casual dates, coffee hangouts, and social mixers.
     */
    CASUAL_DATES,

    /**
     * Users exploring and figuring out their dating journey.
     */
    FIGURING_IT_OUT;

    @JsonCreator
    public static DatingIntent fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        if (normalized.contains("RELATIONSHIP") || normalized.contains("SERIOUS")) {
            return SERIOUS_DATING;
        }
        if (normalized.contains("MARRIAGE") || normalized.contains("MATRIMONY")) {
            return MARRIAGE_MINDED;
        }
        if (normalized.contains("CASUAL")) {
            return CASUAL_DATES;
        }
        if (normalized.contains("FIGURING")) {
            return FIGURING_IT_OUT;
        }
        try {
            return DatingIntent.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
