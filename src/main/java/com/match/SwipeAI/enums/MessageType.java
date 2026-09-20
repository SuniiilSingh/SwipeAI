package com.match.SwipeAI.enums;

/**
 * Message payload type supported in the encrypted chat lounge.
 */
public enum MessageType {
    /**
     * Standard text message.
     */
    TEXT,

    /**
     * Ephemeral or persistent audio note recorded in native dialect.
     */
    AUDIO_NOTE,
    AUDIO,

    /**
     * Image attachment (inspected by Shield 360 AI Safe Detector).
     */
    IMAGE,

    /**
     * Shared Meme DNA snippet.
     */
    MEME,

    /**
     * Virtual Chai invite / WebRTC session notification.
     */
    VIRTUAL_CHAI,

    /**
     * System-generated match status, timer warning, or safety prompt.
     */
    SYSTEM
}
