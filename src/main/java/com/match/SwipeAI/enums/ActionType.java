package com.match.SwipeAI.enums;

/**
 * Type of interaction performed on a profile candidate in the discovery feed.
 */
public enum ActionType {
    /**
     * High-intent contextual like (optionally linked to a photo, voice snippet, prompt, or meme).
     */
    LIKE,

    /**
     * Pass on the candidate.
     */
    PASS,

    /**
     * Micro-invite sachet interaction: "Send Virtual Cutting Chai" (₹21) or Super Spark.
     */
    SUPER_CHAI
}
