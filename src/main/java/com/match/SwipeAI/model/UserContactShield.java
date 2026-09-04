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
 * User Contact Shield (Shadow Shield) Entity.
 * Stores salted SHA-256 phone hashes and corporate email domains.
 * Guarantees zero-knowledge bi-directional invisibility from family members, relatives, and bosses.
 */
@Entity
@Table(name = "user_contact_shields", indexes = {
    @Index(name = "idx_contact_hash", columnList = "contact_phone_hash"),
    @Index(name = "idx_corp_domain", columnList = "corporate_domain")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContactShield {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Salted hash: SHA-256(phone + server_pepper) computed client/server side.
     */
    @Column(name = "contact_phone_hash", nullable = false, length = 64)
    private String contactPhoneHash;

    /**
     * Corporate domain block (e.g. swiggy.in, tcs.com, infosys.com, google.com).
     */
    @Column(name = "corporate_domain", length = 100)
    private String corporateDomain;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
