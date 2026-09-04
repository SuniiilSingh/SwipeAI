package com.match.SwipeAI.enums;

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
    FIGURING_IT_OUT
}
