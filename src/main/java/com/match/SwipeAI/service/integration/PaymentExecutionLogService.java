package com.match.SwipeAI.service.integration;

import com.match.SwipeAI.model.PaymentExecutionLog;
import com.match.SwipeAI.repository.PaymentExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Dedicated persistence and query service for payment execution logs.
 * Saves logs in an isolated REQUIRES_NEW transaction so failure logs persist
 * even if the main transaction is rolled back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentExecutionLogService {

    private final PaymentExecutionLogRepository executionLogRepository;

    /**
     * Records a single execution log entry in an autonomous transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentExecutionLog recordExecution(
            String orderId,
            UUID userId,
            String action,
            String status,
            long executionTimeMs,
            String summary,
            String errorMessage) {
        try {
            // Trim summary to 255 chars if needed
            String cleanSummary = summary != null && summary.length() > 255
                    ? summary.substring(0, 252) + "..."
                    : summary;

            // Trim error message to 500 chars (short exception message only, no stack trace)
            String cleanError = errorMessage != null && errorMessage.length() > 500
                    ? errorMessage.substring(0, 497) + "..."
                    : errorMessage;

            PaymentExecutionLog entry = PaymentExecutionLog.builder()
                    .orderId(orderId)
                    .userId(userId)
                    .action(action)
                    .status(status)
                    .executionTimeMs(executionTimeMs)
                    .summary(cleanSummary)
                    .errorMessage(cleanError)
                    .build();

            return executionLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to record payment execution log for order: {}", orderId, e);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentExecutionLog> getLogsByOrderId(String orderId) {
        return executionLogRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    @Transactional(readOnly = true)
    public List<PaymentExecutionLog> getLogsByUserId(UUID userId) {
        return executionLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<PaymentExecutionLog> getRecentLogs(String status) {
        if (status != null && !status.isBlank()) {
            return executionLogRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        }
        return executionLogRepository.findTop50ByOrderByCreatedAtDesc();
    }
}
