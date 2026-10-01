package com.match.SwipeAI.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Living situation indicator reflecting Indian urban reality.
 * Drastically affects spontaneous dating logistics, curfew flexibility, and date planning.
 */
public enum LivingStatus {
    /**
     * Living with parents / family household.
     */
    WITH_PARENTS,

    /**
     * Living independently in a rented/owned flat or apartment.
     */
    INDEPENDENT_FLAT,

    /**
     * Living in a Paying Guest (PG) accommodation or co-living facility.
     */
    PG;

    @JsonCreator
    public static LivingStatus fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        if (normalized.contains("PARENT")) return WITH_PARENTS;
        if (normalized.contains("FLAT") || normalized.contains("INDEPENDENT")) return INDEPENDENT_FLAT;
        if (normalized.contains("PG") || normalized.contains("HOSTEL")) return PG;
        try {
            return LivingStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
