package com.match.SwipeAI.service.integration;

import com.match.SwipeAI.config.FeatureFlagsProperties;
import com.match.SwipeAI.dto.PaymentDto;
import com.match.SwipeAI.enums.OrderStatus;
import com.match.SwipeAI.enums.PaymentProvider;
import com.match.SwipeAI.enums.SkuType;
import com.match.SwipeAI.model.UpiOrder;
import com.match.SwipeAI.repository.UpiOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Cashfree Payments Gateway Service for Web & Alternate Indian Checkout.
 * Provides order creation, session generation, and HMAC-SHA256 webhook processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CashfreePaymentService {

    private final FeatureFlagsProperties properties;
    private final UpiOrderRepository upiOrderRepository;
    private final UpiPaymentService upiPaymentService;
    private final com.match.SwipeAI.service.engine.PaymentCryptoService paymentCryptoService;
    private final PaymentAuditService paymentAuditService;

    public PaymentDto.CashfreeCreateOrderResponse createOrder(UUID userId, SkuType sku, String customerPhone) {
        return createOrder(userId, sku, customerPhone, "UNKNOWN", "UNKNOWN");
    }

    public PaymentDto.CashfreeCreateOrderResponse createOrder(UUID userId, SkuType sku, String customerPhone, String clientIp, String userAgent) {
        String orderId = "cf_order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        int amountPaise = sku.getAmountPaise();
        int amountInr = amountPaise / 100;

        String paymentSessionId = "session_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String cfOrderId = "cf_" + System.currentTimeMillis();

        UpiOrder order = UpiOrder.builder()
                .orderId(orderId)
                .userId(userId)
                .amountPaise(amountPaise)
                .sku(sku)
                .paymentProvider(PaymentProvider.CASHFREE)
                .currency("INR")
                .status(OrderStatus.PENDING)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .build();

        upiOrderRepository.save(order);
        log.info("Created Cashfree Order {} for user {} SKU {} (phone: {})",
                orderId, userId, sku, paymentCryptoService.maskPhone(customerPhone));

        paymentAuditService.recordEvent(
                orderId,
                userId,
                PaymentProvider.CASHFREE,
                "ORDER_INITIATED",
                OrderStatus.PENDING,
                String.format("Cashfree order initiated for SKU: %s, amount: ₹%d, phone: %s",
                        sku.name(), amountInr, paymentCryptoService.maskPhone(customerPhone)),
                clientIp,
                userAgent
        );

        return PaymentDto.CashfreeCreateOrderResponse.builder()
                .orderId(orderId)
                .paymentSessionId(paymentSessionId)
                .cfOrderId(cfOrderId)
                .orderAmount(amountInr)
                .orderCurrency("INR")
                .sku(sku)
                .simulated(!properties.getFeatures().getCashfree().isEnabled())
                .build();
    }

    @Transactional
    public boolean processWebhook(String signature, String timestamp, Map<String, Object> payload) {
        return processWebhook(signature, timestamp, payload, "GATEWAY_WEBHOOK", "CASHFREE_SERVER");
    }

    @Transactional
    public boolean processWebhook(String signature, String timestamp, Map<String, Object> payload, String clientIp, String userAgent) {
        log.info("Processing Cashfree webhook: timestamp={}, payload={}",
                timestamp, paymentCryptoService.sanitizePayloadForLogging(payload));

        if (properties.getFeatures().getCashfree().isEnabled()) {
            boolean valid = verifyCashfreeSignature(signature, timestamp, payload);
            if (!valid) {
                log.warn("Invalid Cashfree webhook signature!");
                paymentAuditService.recordEvent(
                        "UNKNOWN_ORDER",
                        UUID.fromString("00000000-0000-0000-0000-000000000000"),
                        PaymentProvider.CASHFREE,
                        "SIGNATURE_VERIFICATION_FAILED",
                        OrderStatus.FAILED,
                        "Cashfree webhook signature mismatch",
                        clientIp,
                        userAgent
                );
                return false;
            }
        }

        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        if (data == null) {
            log.warn("Invalid Cashfree webhook: missing 'data'");
            return false;
        }

        Map<String, Object> orderMap = (Map<String, Object>) data.get("order");
        Map<String, Object> paymentMap = (Map<String, Object>) data.get("payment");

        String orderId = orderMap != null ? (String) orderMap.get("order_id") : null;
        String paymentStatus = paymentMap != null ? (String) paymentMap.get("payment_status") : null;
        String paymentId = paymentMap != null ? String.valueOf(paymentMap.get("cf_payment_id")) : null;

        if (orderId == null) {
            log.warn("Missing order_id in Cashfree webhook");
            return false;
        }

        Optional<UpiOrder> optionalOrder = upiOrderRepository.findByOrderId(orderId);
        if (optionalOrder.isEmpty()) {
            log.warn("Order not found for Cashfree orderId: {}", orderId);
            return false;
        }

        UpiOrder order = optionalOrder.get();
        if (order.getStatus() == OrderStatus.CAPTURED) {
            log.info("Order {} already CAPTURED, skipping duplicate event", orderId);
            return true;
        }

        // Store AES-256-GCM encrypted raw payload
        order.setRawPayloadEncrypted(paymentCryptoService.encrypt(payload.toString()));

        if ("SUCCESS".equalsIgnoreCase(paymentStatus) || "PAID".equalsIgnoreCase(paymentStatus)) {
            order.setStatus(OrderStatus.CAPTURED);
            order.setPaymentId(paymentId != null ? paymentId : "cf_pay_" + UUID.randomUUID().toString().substring(0, 8));
            order.setExternalTransactionId(paymentId);
            order.setCapturedAt(OffsetDateTime.now());
            upiOrderRepository.save(order);

            upiPaymentService.grantUserBenefits(order.getUserId(), order.getSku());
            log.info("Successfully captured Cashfree payment for order {}, granted SKU {}", orderId, order.getSku());

            paymentAuditService.recordEvent(
                    orderId,
                    order.getUserId(),
                    PaymentProvider.CASHFREE,
                    "PAYMENT_CAPTURED",
                    OrderStatus.CAPTURED,
                    String.format("Cashfree payment captured. GatewayPaymentId: %s", paymentCryptoService.maskToken(paymentId)),
                    clientIp,
                    userAgent
            );
            paymentAuditService.recordEvent(
                    orderId,
                    order.getUserId(),
                    PaymentProvider.CASHFREE,
                    "PERKS_GRANTED",
                    OrderStatus.CAPTURED,
                    String.format("Perks unlocked for SKU: %s (%s)", order.getSku().name(), order.getSku().getDescription()),
                    clientIp,
                    userAgent
            );
            return true;
        } else {
            order.setStatus(OrderStatus.FAILED);
            order.setFailureReason("Cashfree paymentStatus: " + paymentStatus);
            upiOrderRepository.save(order);

            paymentAuditService.recordEvent(
                    orderId,
                    order.getUserId(),
                    PaymentProvider.CASHFREE,
                    "PAYMENT_FAILED",
                    OrderStatus.FAILED,
                    "Cashfree payment failed with status: " + paymentStatus,
                    clientIp,
                    userAgent
            );
            return false;
        }
    }

    public boolean testConfirmCashfreeOrder(String orderId) {
        Optional<UpiOrder> optionalOrder = upiOrderRepository.findByOrderId(orderId);
        if (optionalOrder.isEmpty()) return false;

        UpiOrder order = optionalOrder.get();
        if (order.getStatus() == OrderStatus.CAPTURED) return true;

        String testPaymentId = "cf_test_pay_" + System.currentTimeMillis();
        order.setStatus(OrderStatus.CAPTURED);
        order.setPaymentId(testPaymentId);
        order.setExternalTransactionId(testPaymentId);
        order.setCapturedAt(OffsetDateTime.now());
        order.setRawPayloadEncrypted(paymentCryptoService.encrypt("{\"simulated\": true, \"orderId\": \"" + orderId + "\"}"));
        upiOrderRepository.save(order);

        upiPaymentService.grantUserBenefits(order.getUserId(), order.getSku());

        paymentAuditService.recordEvent(
                orderId,
                order.getUserId(),
                PaymentProvider.CASHFREE,
                "PAYMENT_CAPTURED",
                OrderStatus.CAPTURED,
                "Test simulated Cashfree capture confirmed",
                "127.0.0.1",
                "INTERNAL_SIMULATOR"
        );
        paymentAuditService.recordEvent(
                orderId,
                order.getUserId(),
                PaymentProvider.CASHFREE,
                "PERKS_GRANTED",
                OrderStatus.CAPTURED,
                String.format("Perks credited for SKU: %s", order.getSku().name()),
                "127.0.0.1",
                "INTERNAL_SIMULATOR"
        );
        return true;
    }

    private boolean verifyCashfreeSignature(String signature, String timestamp, Map<String, Object> payload) {
        try {
            String secretKey = properties.getFeatures().getCashfree().getSecretKey();
            if (secretKey == null || secretKey.isBlank()) return true;

            String dataToSign = timestamp + payload.toString();
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(hash);
            return computed.equals(signature);
        } catch (Exception e) {
            log.error("Error verifying Cashfree signature", e);
            return false;
        }
    }
}
