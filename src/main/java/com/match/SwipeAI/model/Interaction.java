package com.match.SwipeAI.model;

import com.match.SwipeAI.enums.ActionType;
import com.match.SwipeAI.enums.ContextType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * High-Intent Profile Interaction Entity.
 * Records likes, passes, and micro-invites (Virtual Cutting Chai).
 * Tracks contextual target anchors (photo, voice note, prompt, or meme) and user comments.
 */
@Entity
@Table(name = "interactions",
    uniqueConstraints = @UniqueConstraint(name = "unique_actor_target", columnNames = {"actor_id", "target_id"}),
    indexes = @Index(name = "idx_interactions_target", columnList = "target_id, action_type")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Interaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ActionType actionType; // LIKE, PASS, SUPER_CHAI

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", length = 20)
    private ContextType contextType; // PHOTO, VOICE, PROMPT, MEME

    @Column(name = "context_target_id", length = 50)
    private String contextTargetId;

    @Column(name = "comment_text", length = 280)
    private String commentText;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
