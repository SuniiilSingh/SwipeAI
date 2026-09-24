package com.match.SwipeAI.model;

import com.match.SwipeAI.enums.OrderStatus;
import com.match.SwipeAI.enums.PaymentProvider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable Payment Audit Trail Entity.
 * Records every lifecycle step (Order Initiated, Token Received, Signature Verified,
 * Payment Captured, Perks Granted, Failure, Dispute, Manual Support Reconciliation).
 * Sensitive metadata is encrypted at rest using AES-256-GCM.
 */
@Entity
@Table(name = "payment_audit_logs", indexes = {
    @Index(name = "idx_pay_audit_order", columnList = "order_id, created_at ASC"),
    @Index(name = "idx_pay_audit_user", columnList = "user_id, created_at DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", length = 30)
    private PaymentProvider paymentProvider;

    @Column(name = "event", nullable = false, length = 64)
    private String event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "encrypted_metadata", columnDefinition = "TEXT")
    private String encryptedMetadata;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 256)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
