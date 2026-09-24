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
 * Lightweight Payment Execution Log Entity.
 * Automatically recorded via AOP on payment transaction operations.
 * Captures clean one-line summaries on success and concise error messages on failure (no stack traces).
 */
@Entity
@Table(name = "payment_execution_logs", indexes = {
    @Index(name = "idx_pay_exec_order", columnList = "order_id, created_at DESC"),
    @Index(name = "idx_pay_exec_user", columnList = "user_id, created_at DESC"),
    @Index(name = "idx_pay_exec_status", columnList = "status, created_at DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // "SUCCESS" or "FAILED"

    @Column(name = "execution_time_ms", nullable = false)
    private Long executionTimeMs;

    @Column(name = "summary", length = 255)
    private String summary; // Clean 1-line log

    @Column(name = "error_message", length = 512)
    private String errorMessage; // Short exception message only, no stack trace

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
