package com.match.SwipeAI.enums;

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
    NON_VEG
}
