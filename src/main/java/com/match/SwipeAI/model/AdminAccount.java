package com.match.SwipeAI.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Database-backed Admin & Support Portal Account Entity.
 * Supports SUPER_ADMIN (full access) and SUPPORT_AGENT (ticket resolution + read-only user & payment inspection).
 */
@Entity
@Table(name = "admin_accounts", indexes = {
    @Index(name = "idx_admin_accounts_email", columnList = "email", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /**
     * Role: SUPER_ADMIN or SUPPORT_AGENT
     */
    @Column(nullable = false, length = 30)
    private String role;

    /**
     * Supports BCrypt hash ($2a$ / $2b$) via pgcrypto or BCryptPasswordEncoder,
     * as well as direct string match if inserted manually without hashing.
     */
    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    /**
     * 2FA Security PIN (BCrypt hash or direct PIN string).
     */
    @Column(name = "pin_hash", nullable = false, length = 120)
    private String pinHash;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "failed_attempts", nullable = false)
    private Integer failedAttempts = 0;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 64)
    private String lastLoginIp;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
