package com.match.SwipeAI.enums;

/**
 * Lifecycle status of a message inside the encrypted chat lounge.
 */
public enum MessageStatus {
    /**
     * Sent by the sender and encrypted into database storage.
     */
    SENT,

    /**
     * Delivered to the recipient via real-time WebSocket or retrieved on login.
     */
    DELIVERED,

    /**
     * Read by the recipient (read receipt acknowledged).
     */
    READ
}
