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
 * User Push Notification Token Entity.
 * Stores Expo Push Tokens (e.g. ExponentPushToken[...]) or native APNs/FCM tokens
 * for dispatching high-priority mutual match, chat, and icebreaker alerts.
 */
@Entity
@Table(name = "push_tokens",
    uniqueConstraints = @UniqueConstraint(name = "unique_user_token", columnNames = {"user_id", "token"}),
    indexes = {
        @Index(name = "idx_push_token_user", columnList = "user_id"),
        @Index(name = "idx_push_token_val", columnList = "token")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String token;

    @Builder.Default
    @Column(length = 30)
    private String platform = "ANDROID"; // ANDROID, IOS, WEB

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
