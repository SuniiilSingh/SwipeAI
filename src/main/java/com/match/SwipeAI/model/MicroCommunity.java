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
 * Micro-Community Hub Entity.
 * Represents localized lifestyle tribes and neighborhood circles across India
 * (e.g. Koramangala Startup Builders, Bandra West Creatives, North Campus DU).
 */
@Entity
@Table(name = "micro_communities", indexes = {
    @Index(name = "idx_micro_comm_city", columnList = "city"),
    @Index(name = "idx_micro_comm_slug", columnList = "slug", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MicroCommunity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String city;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(length = 255)
    private String tagline;

    @Column(name = "vibe_category", length = 50)
    private String vibeCategory;

    @Column(name = "badge_icon", length = 30)
    private String badgeIcon;

    @Column(name = "icon_name", length = 50)
    private String iconName;

    @Column(name = "center_lat")
    private Double centerLat;

    @Column(name = "center_lng")
    private Double centerLng;

    @Builder.Default
    @Column(name = "is_popular")
    private Boolean isPopular = true;

    @Builder.Default
    @Column(name = "active_members_count")
    private Integer activeMembersCount = 150;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
