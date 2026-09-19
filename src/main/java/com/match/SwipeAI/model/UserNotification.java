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
 * In-App User Notification Entity.
 * Stores persistent notifications for the user's in-app notification center/inbox,
 * supporting unread indicators and deep-link routing.
 */
@Entity
@Table(name = "user_notifications",
    indexes = {
        @Index(name = "idx_user_notif_user_created", columnList = "user_id, created_at DESC"),
        @Index(name = "idx_user_notif_user_read", columnList = "user_id, is_read")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String type; // MATCH, CHAT, CHAT_UNLOCKED, SYSTEM

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(name = "data_json", columnDefinition = "TEXT")
    private String dataJson;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
