package com.match.SwipeAI.model;

import com.match.SwipeAI.enums.DatingIntent;
import com.match.SwipeAI.enums.Gender;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Core User Entity.
 * Represents registered account, verification status (DigiLocker ZK & WhatsApp),
 * dynamic anti-ghosting Karma rating (0-200), and spatial coordinates for Uber H3 clustering.
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_discovery", columnList = "gender, intent, birth_date"),
    @Index(name = "idx_users_lat_lng", columnList = "latitude, longitude")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "phone_e164", unique = true, nullable = false, length = 20)
    private String phoneE164;

    @Builder.Default
    @Column(name = "whatsapp_verified")
    private Boolean whatsappVerified = false;

    @Builder.Default
    @Column(name = "digilocker_verified")
    private Boolean digilockerVerified = false;

    /**
     * SHA-256 hash of DigiLocker subject ID to prevent multiple account creations
     * without storing raw Aadhaar numbers.
     */
    @Column(name = "digilocker_sub_hash", unique = true, length = 64)
    private String digilockerSubHash;

    /**
     * 3D Biometric Liveness score (0.00 - 1.00). >= 0.85 indicates verified live human.
     */
    @Builder.Default
    @Column(name = "liveness_score")
    private Double livenessScore = 0.0;

    /**
     * Dynamic Ghost-Buster Karma score (0 - 200, default 100).
     * High karma grants profile spotlighting; ghosters get deprioritized.
     */
    @Builder.Default
    @Column(name = "karma_score")
    private Integer karmaScore = 100;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DatingIntent intent;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Builder.Default
    @Column(name = "is_incognito")
    private Boolean isIncognito = false;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "h3_index_res8")
    private Long h3IndexRes8;

    @Builder.Default
    @Column(name = "sparks_balance")
    private Integer sparksBalance = 3;

    @Builder.Default
    @Column(name = "boosts_balance")
    private Integer boostsBalance = 0;

    @Builder.Default
    @Column(name = "direct_dms_balance")
    private Integer directDmsBalance = 0;

    @Builder.Default
    @Column(name = "has_active_pass")
    private Boolean hasActivePass = false;

    @Column(name = "pass_expiry")
    private OffsetDateTime passExpiry;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
