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
 * Account Deletion & User Churn Feedback Log.
 * Stores permanent historical records of why users deleted their accounts (DPDP compliance),
 * including churn reasons, optional remarks, phone numbers, and delivery channel (web OTP vs in-app).
 * Allows admins to analyze churn patterns, address product feedback, and take corrective actions.
 */
@Entity
@Table(name = "account_deletion_logs", indexes = {
    @Index(name = "idx_del_phone", columnList = "phone_e164"),
    @Index(name = "idx_del_created", columnList = "created_at DESC"),
    @Index(name = "idx_del_reason", columnList = "reason_category")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "phone_e164", nullable = false, length = 32)
    private String phoneE164;

    @Column(name = "phone_masked", length = 32)
    private String phoneMasked;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "source", nullable = false, length = 32)
    private String source; // WEB_OTP_VERIFIED, IN_APP_SETTINGS, ADMIN_ACTION

    @Column(name = "reason_category", nullable = false, length = 64)
    private String reasonCategory; // FOUND_MATCH, DATING_BREAK, PRIVACY_CONCERNS, NOT_ENOUGH_MATCHES, TECHNICAL_BUGS, EXPENSIVE_PERKS, OTHER

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 32)
    private String status = "COMPLETED"; // COMPLETED, REVIEWED, ACTION_TAKEN

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
