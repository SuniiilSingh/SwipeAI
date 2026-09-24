package com.match.SwipeAI.aspect;

import com.match.SwipeAI.annotation.TrackPaymentTransaction;
import com.match.SwipeAI.dto.PaymentDto;
import com.match.SwipeAI.service.integration.PaymentExecutionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Aspect-Oriented Programming (AOP) Payment Transaction Tracker.
 * Non-intrusively wraps payment methods to guarantee execution logging.
 * Produces clean one-line logs on success, and records only concise exception messages on failure (no stack traces).
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PaymentTrackingAspect {

    private final PaymentExecutionLogService executionLogService;

    @Around("@annotation(trackPayment)")
    public Object trackTransaction(ProceedingJoinPoint joinPoint, TrackPaymentTransaction trackPayment) throws Throwable {
        long startTime = System.currentTimeMillis();
        String action = resolveAction(joinPoint, trackPayment);
        UUID userId = extractUserId(joinPoint);
        String orderId = extractOrderIdFromArgs(joinPoint);

        try {
            Object result = joinPoint.proceed();
            long durationMs = System.currentTimeMillis() - startTime;

            // If orderId was generated during method execution, extract from result
            if (orderId == null) {
                orderId = extractOrderIdFromResult(result);
            }

            String summary = String.format("SUCCESS: [%s] orderId=%s | latency=%dms",
                    action, orderId != null ? orderId : "N/A", durationMs);
            log.info(summary);

            executionLogService.recordExecution(
                    orderId,
                    userId,
                    action,
                    "SUCCESS",
                    durationMs,
                    summary,
                    null
            );

            return result;
        } catch (Throwable ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            String errorMsg = ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getMessage()
                    : ex.getClass().getSimpleName();

            String summary = String.format("FAILED: [%s] orderId=%s | Error: %s | latency=%dms",
                    action, orderId != null ? orderId : "N/A", errorMsg, durationMs);
            log.warn(summary);

            executionLogService.recordExecution(
                    orderId,
                    userId,
                    action,
                    "FAILED",
                    durationMs,
                    summary,
                    errorMsg
            );

            throw ex;
        }
    }

    private String resolveAction(ProceedingJoinPoint joinPoint, TrackPaymentTransaction trackPayment) {
        if (trackPayment.action() != null && !trackPayment.action().isBlank()) {
            return trackPayment.action();
        }
        return joinPoint.getSignature().getName().toUpperCase();
    }

    private UUID extractUserId(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof UUID uuid) {
                return uuid;
            }
        }
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UUID principalUuid) {
                return principalUuid;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractOrderIdFromArgs(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof String str) {
                if (str.startsWith("order_") || str.startsWith("iap_") || str.startsWith("cf_")) {
                    return str;
                }
            } else if (arg instanceof PaymentDto.IapVerifyRequest iapReq) {
                if (iapReq.getOrderId() != null && !iapReq.getOrderId().isBlank()) {
                    return iapReq.getOrderId();
                }
            } else if (arg instanceof Map<?, ?> map) {
                String extracted = extractFromMap(map);
                if (extracted != null) return extracted;
            }
        }
        return null;
    }

    private String extractOrderIdFromResult(Object result) {
        if (result == null) return null;
        Object body = result;
        if (result instanceof ResponseEntity<?> responseEntity) {
            body = responseEntity.getBody();
        }

        if (body instanceof PaymentDto.CreateOrderResponse resp) {
            return resp.getOrderId();
        } else if (body instanceof PaymentDto.IapVerifyResponse resp) {
            return resp.getOrderId();
        } else if (body instanceof PaymentDto.CashfreeCreateOrderResponse resp) {
            return resp.getOrderId();
        } else if (body instanceof PaymentDto.PaymentAuditTimelineDto resp) {
            return resp.getOrderId();
        } else if (body instanceof Map<?, ?> map) {
            Object id = map.get("orderId");
            if (id != null) return id.toString();
        }
        return null;
    }

    private String extractFromMap(Map<?, ?> map) {
        if (map.containsKey("order_id")) {
            return String.valueOf(map.get("order_id"));
        }
        if (map.containsKey("orderId")) {
            return String.valueOf(map.get("orderId"));
        }
        // Razorpay / Cashfree nested structures
        Object payload = map.get("payload");
        if (payload instanceof Map<?, ?> payloadMap) {
            Object payment = payloadMap.get("payment");
            if (payment instanceof Map<?, ?> paymentMap && paymentMap.containsKey("order_id")) {
                return String.valueOf(paymentMap.get("order_id"));
            }
        }
        Object data = map.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object order = dataMap.get("order");
            if (order instanceof Map<?, ?> orderMap && orderMap.containsKey("order_id")) {
                return String.valueOf(orderMap.get("order_id"));
            }
        }
        return null;
    }
}
