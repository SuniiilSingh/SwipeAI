package com.match.SwipeAI.enums;

/**
 * State machine stages for the match lifecycle.
 * Transitions:
 * MUTUAL_LIKE -> PENDING_ICEBREAKER (Starts 48h timer)
 * QUIZ_COMPLETED -> ACTIVE_CHAT (Unlocks WebSocket chat lounge)
 * 48H_TIMEOUT (< 4 msgs) -> EXPIRED (Deducts initiator karma by -8)
 * USER_CLICKS_UNMATCH -> UNMATCHED (Severs WebRTC and chat IDs)
 */
public enum MatchStatus {
    /**
     * Initial match formed. Awaiting 10-second rapid-fire icebreaker quiz completion.
     */
    PENDING_ICEBREAKER,

    /**
     * Icebreaker completed. Text messaging and WebRTC Virtual Chai call unlocked.
     */
    ACTIVE_CHAT,

    /**
     * 48-hour ephemeral timer expired with insufficient conversation activity.
     */
    EXPIRED,

    /**
     * Real-world date scheduled or contact info exchanged.
     * Archived into the Date Book, freeing up an active match slot under the Rule of 5.
     */
    DATE_PLANNED,

    /**
     * Mutual match formed while user is at maximum active capacity (5/5).
     * Held in priority queue until an active chat slot frees up.
     */
    QUEUED_CAPACITY,

    /**
     * Unmatched by either user.
     */
    UNMATCHED
}
