package com.match.SwipeAI.model;

import com.match.SwipeAI.enums.MatchStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * State Machine Match Entity.
 * Controls the progression of matches from PENDING_ICEBREAKER -> ACTIVE_CHAT -> EXPIRED/UNMATCHED.
 * Tracks the 48-hour countdown timer and pre-chat 10-second rapid-fire quiz payload.
 */
@Entity
@Table(name = "matches",
    uniqueConstraints = @UniqueConstraint(name = "unique_match_pair", columnNames = {"user_a_id", "user_b_id"}),
    indexes = {
        @Index(name = "idx_matches_users", columnList = "user_a_id, user_b_id, status"),
        @Index(name = "idx_matches_user_a_active", columnList = "user_a_id, status, matched_at DESC"),
        @Index(name = "idx_matches_user_b_active", columnList = "user_b_id, status, matched_at DESC"),
        @Index(name = "idx_matches_status_expires", columnList = "status, expires_at"),
        @Index(name = "idx_matches_reverse_pair", columnList = "user_b_id, user_a_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_a_id", nullable = false)
    private UUID userAId;

    @Column(name = "user_b_id", nullable = false)
    private UUID userBId;

    @Column(name = "initiator_id")
    private UUID initiatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private MatchStatus status = MatchStatus.PENDING_ICEBREAKER;

    /**
     * Serialized JSON payload containing the 10-second rapid fire icebreaker state,
     * options, and user answers.
     */
    @Column(name = "icebreaker_game_data", columnDefinition = "TEXT")
    private String icebreakerGameData;

    @Builder.Default
    @Column(name = "messages_count")
    private Integer messagesCount = 0;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "matched_at", updatable = false)
    private OffsetDateTime matchedAt;
}
