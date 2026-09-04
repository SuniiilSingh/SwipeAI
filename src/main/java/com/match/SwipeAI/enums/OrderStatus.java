package com.match.SwipeAI.enums;

/**
 * Processing status of a UPI micro-sachet order.
 */
public enum OrderStatus {
    /**
     * Order initiated, awaiting user payment via UPI intent or webhook confirmation.
     */
    PENDING,

    /**
     * Payment successfully verified and captured. Benefits credited to user account.
     */
    CAPTURED,

    /**
     * Payment failed or timed out.
     */
    FAILED
}
