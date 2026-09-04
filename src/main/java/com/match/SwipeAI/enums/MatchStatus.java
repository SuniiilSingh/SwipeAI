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
     * Unmatched by either user.
     */
    UNMATCHED
}
