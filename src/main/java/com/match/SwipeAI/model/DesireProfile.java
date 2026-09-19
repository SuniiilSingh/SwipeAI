package com.match.SwipeAI.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a user's multi-dimensional Desire Profile (Partner Blueprint).
 * Stores non-negotiable boundaries, weekend rhythm, chemistry style, and AI green flags.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_desire_profiles")
public class DesireProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Builder.Default
    @Column(name = "is_configured")
    private Boolean isConfigured = false;

    // Dimension 1: Foundations & Boundaries
    @Builder.Default
    @Column(name = "min_age")
    private Integer minAge = 21;

    @Builder.Default
    @Column(name = "max_age")
    private Integer maxAge = 35;

    @Builder.Default
    @Column(name = "age_flexible")
    private Boolean ageFlexible = true;

    @Builder.Default
    @Column(name = "max_distance_km")
    private Integer maxDistanceKm = 50;

    @Builder.Default
    @Column(name = "dietary_harmony", length = 50)
    private String dietaryHarmony = "ANY_DIET";

    @Builder.Default
    @Column(name = "smoking_comfort", length = 50)
    private String smokingComfort = "NO_PREFERENCE";

    @Builder.Default
    @Column(name = "drinking_comfort", length = 50)
    private String drinkingComfort = "NO_PREFERENCE";

    @Builder.Default
    @Column(name = "living_situation_comfort", length = 50)
    private String livingSituationComfort = "NO_PREFERENCE";

    @Builder.Default
    @Column(name = "relationship_intent_match", length = 50)
    private String relationshipIntentMatch = "ANY";

    // Dimension 2: Weekend Rhythm & Date Vibe
    @Builder.Default
    @Column(name = "weekend_vibe", length = 100)
    private String weekendVibe = "COFFEE_AND_BOOKS";

    // Dimension 3: Communication & Chemistry
    @Builder.Default
    @Column(name = "communication_pace", length = 100)
    private String communicationPace = "VOICE_NOTES_AND_MEMES";

    @Builder.Default
    @Column(name = "banter_style", length = 100)
    private String banterStyle = "DRY_WIT";

    @Builder.Default
    @Column(name = "love_language", length = 100)
    private String loveLanguage = "QUALITY_TIME";

    // Dimension 4: Secret Green Flags & AI Natural Language Prompt
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "desire_green_flags", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "green_flag")
    @Builder.Default
    private List<String> greenFlags = new ArrayList<>();

    // Preferred Partner Professions (Multi-Select)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "desire_preferred_professions",
        joinColumns = @JoinColumn(name = "user_id"),
        indexes = @Index(name = "idx_desire_professions", columnList = "user_id, profession"))
    @Column(name = "profession")
    @Builder.Default
    private List<String> preferredProfessions = new ArrayList<>();

    @Column(name = "natural_language_prompt", length = 512)
    private String naturalLanguagePrompt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
