package com.match.SwipeAI.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Represents the biological / expressed gender of a user.
 * Used for preference matching, gender ratio balancing, and safety protocols.
 */
public enum Gender {
    /**
     * Male user.
     */
    MALE,

    /**
     * Female user.
     */
    FEMALE,

    /**
     * Non-binary user.
     */
    NON_BINARY;

    @JsonCreator
    public static Gender fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        if (normalized.equals("MALE") || normalized.equals("MAN")) {
            return MALE;
        }
        if (normalized.equals("FEMALE") || normalized.equals("WOMAN")) {
            return FEMALE;
        }
        if (normalized.contains("NON") || normalized.contains("BINARY")) {
            return NON_BINARY;
        }
        try {
            return Gender.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
