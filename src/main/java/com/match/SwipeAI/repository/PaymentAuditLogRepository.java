package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.PaymentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access repository for immutable payment audit logs.
 */
@Repository
public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLog, UUID> {
    List<PaymentAuditLog> findByOrderIdOrderByCreatedAtAsc(String orderId);
    List<PaymentAuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
