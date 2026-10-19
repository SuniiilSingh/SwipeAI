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
 * Trust & Safety User Report Entity.
 * Tracks user-submitted reports on profiles or chat conversations with category reasons and remarks.
 * Allows Trust & Safety admin to review report count per profile, see reporters, and send resolution actions.
 */
@Entity
@Table(name = "user_reports", indexes = {
    @Index(name = "idx_user_reports_target", columnList = "reported_user_id"),
    @Index(name = "idx_user_reports_reporter", columnList = "reporter_id"),
    @Index(name = "idx_user_reports_status", columnList = "status"),
    @Index(name = "idx_user_reports_created", columnList = "created_at DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "reported_user_id", nullable = false)
    private UUID reportedUserId;

    @Column(name = "match_id")
    private UUID matchId;

    @Column(nullable = false, length = 100)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Builder.Default
    @Column(nullable = false, length = 32)
    private String status = "PENDING"; // PENDING, RESOLVED, DISMISSED

    @Column(name = "admin_action", length = 50)
    private String adminAction; // PROFILE_SUSPENDED, WARNING_ISSUED, CONTENT_REMOVED, DISMISSED

    @Column(name = "admin_message", columnDefinition = "TEXT")
    private String adminMessage;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
