package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.PaymentExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access repository for lightweight payment execution logs.
 */
@Repository
public interface PaymentExecutionLogRepository extends JpaRepository<PaymentExecutionLog, UUID> {
    List<PaymentExecutionLog> findByOrderIdOrderByCreatedAtDesc(String orderId);
    List<PaymentExecutionLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<PaymentExecutionLog> findByStatusOrderByCreatedAtDesc(String status);
    List<PaymentExecutionLog> findTop50ByOrderByCreatedAtDesc();
}
