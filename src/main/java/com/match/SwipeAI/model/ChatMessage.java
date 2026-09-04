package com.match.SwipeAI.model;

import com.match.SwipeAI.enums.MessageType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Chat Message Entity.
 * Represents encrypted message bubbles inside an active match.
 * Protected by Shield 360 AI Safe Detector which flags and auto-blurs unsolicited/sensitive media.
 */
@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_chat_match", columnList = "match_id, created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "media_url", length = 512)
    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", length = 30)
    @Builder.Default
    private MessageType mediaType = MessageType.TEXT;

    @Builder.Default
    @Column(name = "is_blurred")
    private Boolean isBlurred = false;

    @Column(name = "blur_reason")
    private String blurReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
