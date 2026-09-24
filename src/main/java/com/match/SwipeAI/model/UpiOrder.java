package com.match.SwipeAI.model;

import com.match.SwipeAI.enums.OrderStatus;
import com.match.SwipeAI.enums.SkuType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Micro-Sachet UPI Order Entity.
 * Tracks instant UPI purchases (₹19–₹99) and supports idempotent webhook verification.
 */
@Entity
@Table(name = "upi_orders", indexes = {
    @Index(name = "idx_upi_order_id", columnList = "order_id", unique = true),
    @Index(name = "idx_upi_user", columnList = "user_id, status"),
    @Index(name = "idx_upi_user_created", columnList = "user_id, created_at DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpiOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount_paise", nullable = false)
    private Integer amountPaise;

    @Column(length = 10)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SkuType sku;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", length = 30)
    @Builder.Default
    private com.match.SwipeAI.enums.PaymentProvider paymentProvider = com.match.SwipeAI.enums.PaymentProvider.RAZORPAY_UPI;

    @Column(name = "vpa", length = 100)
    private String vpa;

    @Column(name = "upi_intent_url", length = 512)
    private String upiIntentUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
