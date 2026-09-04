package com.match.SwipeAI.enums;

/**
 * Contextual media/prompt target for high-intent profile interactions.
 * Replaces mindless photo swiping with conversational prompt-level comments.
 */
public enum ContextType {
    /**
     * Interaction anchored on a specific profile photo.
     */
    PHOTO,

    /**
     * Interaction anchored on a playable voice prompt audio snippet.
     */
    VOICE,

    /**
     * Interaction anchored on a written cultural prompt or bio statement.
     */
    PROMPT,

    /**
     * Interaction anchored on a Meme DNA card or humor snippet.
     */
    MEME
}
