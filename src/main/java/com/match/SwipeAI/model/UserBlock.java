package com.match.SwipeAI.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Trust & Safety User Block Entity.
 * Tracks users blocked by other users, supporting mutual discovery exclusion
 * and bidirectional in-app unblock capabilities.
 */
@Entity
@Table(name = "user_blocks",
    uniqueConstraints = @UniqueConstraint(name = "unique_blocker_blocked", columnNames = {"blocker_id", "blocked_user_id"}),
    indexes = {
        @Index(name = "idx_user_blocks_blocker", columnList = "blocker_id"),
        @Index(name = "idx_user_blocks_blocked", columnList = "blocked_user_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Column(name = "blocked_user_id", nullable = false)
    private UUID blockedUserId;

    @Column(name = "match_id")
    private UUID matchId;

    @Column(length = 100)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
