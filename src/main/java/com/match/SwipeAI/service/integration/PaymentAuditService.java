package com.match.SwipeAI.service.integration;

import com.match.SwipeAI.dto.PaymentDto;
import com.match.SwipeAI.enums.OrderStatus;
import com.match.SwipeAI.enums.PaymentProvider;
import com.match.SwipeAI.model.PaymentAuditLog;
import com.match.SwipeAI.model.UpiOrder;
import com.match.SwipeAI.repository.PaymentAuditLogRepository;
import com.match.SwipeAI.repository.UpiOrderRepository;
import com.match.SwipeAI.service.engine.PaymentCryptoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Enterprise Payment Audit and Forensic Traceability Service.
 * Manages immutable, tamper-evident audit logs with AES-256-GCM encryption at rest.
 * Provides support and compliance teams with full lifecycle transaction inspection and manual reconciliation.
 */
@Slf4j
@Service
public class PaymentAuditService {

    private final PaymentAuditLogRepository auditLogRepository;
    private final UpiOrderRepository upiOrderRepository;
    private final PaymentCryptoService paymentCryptoService;
    private final UpiPaymentService upiPaymentService;

    public PaymentAuditService(
            PaymentAuditLogRepository auditLogRepository,
            UpiOrderRepository upiOrderRepository,
            PaymentCryptoService paymentCryptoService,
            @Lazy UpiPaymentService upiPaymentService) {
        this.auditLogRepository = auditLogRepository;
        this.upiOrderRepository = upiOrderRepository;
        this.paymentCryptoService = paymentCryptoService;
        this.upiPaymentService = upiPaymentService;
    }

    /**
     * Records an immutable event in the payment lifecycle with AES-256-GCM encrypted metadata.
     */
    @Transactional
    public void recordEvent(
            String orderId,
            UUID userId,
            PaymentProvider provider,
            String event,
            OrderStatus status,
            String plainMetadata,
            String clientIp,
            String userAgent) {
        try {
            String encryptedMetadata = paymentCryptoService.encrypt(plainMetadata);

            PaymentAuditLog auditLog = PaymentAuditLog.builder()
                    .orderId(orderId)
                    .userId(userId)
                    .paymentProvider(provider)
                    .event(event)
                    .status(status)
                    .encryptedMetadata(encryptedMetadata)
                    .clientIp(clientIp)
                    .userAgent(userAgent)
                    .build();

            auditLogRepository.save(auditLog);
            log.info("Audit event recorded: orderId={}, event={}, status={}, provider={}",
                    orderId, event, status, provider);
        } catch (Exception e) {
            log.error("Failed to record payment audit event for orderId: {}", orderId, e);
        }
    }

    /**
     * Retrieves the complete forensic audit timeline for a specific order.
     * Decrypts AES-256-GCM encrypted metadata and payloads for authorized review.
     */
    @Transactional(readOnly = true)
    public PaymentDto.PaymentAuditTimelineDto getOrderAuditTimeline(String orderId) {
        UpiOrder order = upiOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment order not found: " + orderId));

        List<PaymentAuditLog> logs = auditLogRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        List<PaymentDto.PaymentAuditEventDto> eventDtos = logs.stream().map(l ->
                PaymentDto.PaymentAuditEventDto.builder()
                        .id(l.getId())
                        .event(l.getEvent())
                        .status(l.getStatus())
                        .decryptedMetadata(paymentCryptoService.decrypt(l.getEncryptedMetadata()))
                        .clientIp(l.getClientIp())
                        .userAgent(l.getUserAgent())
                        .timestamp(l.getCreatedAt())
                        .build()
        ).toList();

        return PaymentDto.PaymentAuditTimelineDto.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .paymentProvider(order.getPaymentProvider())
                .sku(order.getSku())
                .amountPaise(order.getAmountPaise())
                .currency(order.getCurrency())
                .status(order.getStatus())
                .paymentId(paymentCryptoService.maskToken(order.getPaymentId()))
                .externalTransactionId(order.getExternalTransactionId())
                .clientIp(order.getClientIp())
                .userAgent(order.getUserAgent())
                .failureReason(order.getFailureReason())
                .adminNotes(order.getAdminNotes())
                .reviewedBy(order.getReviewedBy())
                .createdAt(order.getCreatedAt())
                .capturedAt(order.getCapturedAt())
                .updatedAt(order.getUpdatedAt())
                .decryptedRawPayload(paymentCryptoService.decrypt(order.getRawPayloadEncrypted()))
                .events(eventDtos)
                .build();
    }

    /**
     * Retrieves all payment transactions and audit histories for a specific user.
     */
    @Transactional(readOnly = true)
    public List<PaymentDto.PaymentAuditTimelineDto> getUserPaymentAuditHistory(UUID userId) {
        List<UpiOrder> orders = upiOrderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        return orders.stream().map(order -> getOrderAuditTimeline(order.getOrderId())).toList();
    }

    /**
     * Allows customer support / fraud ops to manually inspect, annotate, and reconcile an order.
     * Optionally credits user benefits if manually reconciling a stuck or disputed transaction.
     */
    @Transactional
    public PaymentDto.PaymentAuditTimelineDto reviewAndReconcileOrder(
            String orderId,
            PaymentDto.ReviewOrderRequest request,
            UUID adminUserId) {
        UpiOrder order = upiOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment order not found: " + orderId));

        OrderStatus previousStatus = order.getStatus();
        OrderStatus newStatus = request.getStatus() != null ? request.getStatus() : order.getStatus();
        String reviewer = request.getAdminIdOrName() != null && !request.getAdminIdOrName().isBlank()
                ? request.getAdminIdOrName()
                : (adminUserId != null ? adminUserId.toString() : "SUPPORT_ADMIN");

        order.setStatus(newStatus);
        order.setAdminNotes(request.getAdminNotes());
        order.setReviewedBy(reviewer);

        if (newStatus == OrderStatus.CAPTURED && order.getCapturedAt() == null) {
            order.setCapturedAt(OffsetDateTime.now());
        }
        upiOrderRepository.save(order);

        // If manually marked captured and perks requested, grant user perks idempotently
        if (request.isGrantPerks() && newStatus == OrderStatus.CAPTURED && previousStatus != OrderStatus.CAPTURED) {
            upiPaymentService.grantUserBenefits(order.getUserId(), order.getSku());
            recordEvent(
                    orderId,
                    order.getUserId(),
                    order.getPaymentProvider(),
                    "PERKS_GRANTED_MANUAL_RECONCILIATION",
                    OrderStatus.CAPTURED,
                    "Manual admin override granted benefits for SKU: " + order.getSku() + " by " + reviewer,
                    "INTERNAL_ADMIN",
                    "ADMIN_DASHBOARD"
            );
        }

        recordEvent(
                orderId,
                order.getUserId(),
                order.getPaymentProvider(),
                "MANUAL_SUPPORT_REVIEW",
                newStatus,
                "Reviewed by " + reviewer + " | Status change: " + previousStatus + " -> " + newStatus +
                        " | Notes: " + (request.getAdminNotes() != null ? request.getAdminNotes() : "N/A"),
                "INTERNAL_ADMIN",
                "ADMIN_DASHBOARD"
        );

        return getOrderAuditTimeline(orderId);
    }
}
