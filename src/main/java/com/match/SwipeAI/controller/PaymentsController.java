package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.PaymentDto;
import com.match.SwipeAI.service.engine.PaymentCryptoService;
import com.match.SwipeAI.service.integration.CashfreePaymentService;
import com.match.SwipeAI.service.integration.IapVerificationService;
import com.match.SwipeAI.service.integration.PaymentAuditService;
import com.match.SwipeAI.service.integration.UpiPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise Monetization, Webhook, and Payment Audit Controller.
 * Provides micro-pricing (₹19–₹99) catalog, generates NPCI UPI deep-links, verifies native IAP receipts,
 * processes Cashfree checkouts, and exposes encrypted forensic audit trails for customer support/fraud ops.
 */
@Slf4j
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentsController {

    private final UpiPaymentService upiPaymentService;
    private final IapVerificationService iapVerificationService;
    private final CashfreePaymentService cashfreePaymentService;
    private final PaymentAuditService paymentAuditService;
    private final PaymentCryptoService paymentCryptoService;

    /**
     * Retrieve the full micro-sachet catalog and Weekend Dating Pass pricing.
     */
    @GetMapping("/store/catalog")
    public ResponseEntity<List<PaymentDto.CatalogItemDto>> getCatalog() {
        List<PaymentDto.CatalogItemDto> catalog = upiPaymentService.getCatalog();
        return ResponseEntity.ok(catalog);
    }

    /**
     * Initiate a micro-sachet transaction and generate an NPCI UPI deep-link intent string.
     */
    @PostMapping("/upi/create-order")
    public ResponseEntity<PaymentDto.CreateOrderResponse> createOrder(
            @AuthenticationPrincipal UUID userId,
            @RequestBody PaymentDto.CreateOrderRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);
        log.info("Create UPI Order request: user={}, sku={}, vpa={}",
                userId, request.getSku(), paymentCryptoService.maskVpa(request.getVpa()));

        PaymentDto.CreateOrderResponse response = upiPaymentService.createOrder(
                userId, request.getSku(), request.getVpa(), clientIp, userAgent);
        return ResponseEntity.ok(response);
    }

    /**
     * Idempotent payment webhook handler with HMAC-SHA256 signature verification.
     * Automatically credits user balances upon capture.
     */
    @PostMapping("/upi/webhook")
    public ResponseEntity<Map<String, String>> handleUpiWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);
        log.info("Received UPI Webhook (sanitized): {}", paymentCryptoService.sanitizePayloadForLogging(payload));

        boolean processed = upiPaymentService.processWebhook(signature, idempotencyKey, payload, clientIp, userAgent);
        return ResponseEntity.ok(Map.of(
                "status", processed ? "success" : "ignored",
                "message", processed ? "Payment captured and benefits credited" : "Order already processed or skipped"
        ));
    }

    /**
     * Demo simulation endpoint for developer testing without live payment webhooks.
     */
    @PostMapping("/upi/test-confirm/{orderId}")
    public ResponseEntity<Map<String, String>> testConfirmOrder(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String orderId,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);

        Map<String, Object> mockPayload = Map.of(
                "event", "payment.captured",
                "payload", Map.of(
                        "payment", Map.of(
                                "id", "pay_simulated_" + UUID.randomUUID().toString().substring(0, 8),
                                "order_id", orderId,
                                "status", "captured",
                                "method", "upi"
                        )
                )
        );
        boolean processed = upiPaymentService.processWebhook(null, "idem_" + orderId, mockPayload, clientIp, userAgent);
        return ResponseEntity.ok(Map.of("status", processed ? "success" : "failed", "orderId", orderId));
    }

    /**
     * Verify and credit Native In-App Purchases (Google Play Billing / Apple StoreKit).
     */
    @PostMapping("/iap/verify")
    public ResponseEntity<PaymentDto.IapVerifyResponse> verifyIapPurchase(
            @AuthenticationPrincipal UUID userId,
            @RequestBody PaymentDto.IapVerifyRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);

        log.info("IAP Verification Request: user={}, platform={}, productId={}, orderId={}, token={}",
                userId, request.getPlatform(), request.getProductId(), request.getOrderId(),
                paymentCryptoService.maskToken(request.getPurchaseToken()));

        PaymentDto.IapVerifyResponse response = iapVerificationService.verifyAndCreditPurchase(
                userId, request, clientIp, userAgent);
        return ResponseEntity.ok(response);
    }

    /**
     * Create Cashfree Checkout Order for web or external payment flows.
     */
    @PostMapping("/cashfree/create-order")
    public ResponseEntity<PaymentDto.CashfreeCreateOrderResponse> createCashfreeOrder(
            @AuthenticationPrincipal UUID userId,
            @RequestBody PaymentDto.CashfreeCreateOrderRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);

        log.info("Create Cashfree Order: user={}, sku={}, phone={}",
                userId, request.getSku(), paymentCryptoService.maskPhone(request.getCustomerPhone()));

        PaymentDto.CashfreeCreateOrderResponse response = cashfreePaymentService.createOrder(
                userId, request.getSku(), request.getCustomerPhone(), clientIp, userAgent);
        return ResponseEntity.ok(response);
    }

    /**
     * Cashfree Webhook Handler with HMAC-SHA256 signature verification.
     */
    @PostMapping("/cashfree/webhook")
    public ResponseEntity<Map<String, String>> handleCashfreeWebhook(
            @RequestHeader(value = "x-webhook-signature", required = false) String signature,
            @RequestHeader(value = "x-webhook-timestamp", required = false) String timestamp,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);
        log.info("Received Cashfree Webhook (sanitized): {}", paymentCryptoService.sanitizePayloadForLogging(payload));

        boolean processed = cashfreePaymentService.processWebhook(signature, timestamp, payload, clientIp, userAgent);
        return ResponseEntity.ok(Map.of(
                "status", processed ? "success" : "ignored",
                "message", processed ? "Cashfree payment captured and benefits credited" : "Order already processed or skipped"
        ));
    }

    /**
     * Demo simulation endpoint for developer testing of Cashfree payments.
     */
    @PostMapping("/cashfree/test-confirm/{orderId}")
    public ResponseEntity<Map<String, String>> testConfirmCashfreeOrder(
            @PathVariable String orderId) {
        boolean processed = cashfreePaymentService.testConfirmCashfreeOrder(orderId);
        return ResponseEntity.ok(Map.of(
                "status", processed ? "success" : "failed",
                "orderId", orderId
        ));
    }

    /**
     * Inspect full forensic audit timeline for a specific payment order.
     * Decrypts payloads safely for support/admin reconciliation.
     */
    @GetMapping("/audit/orders/{orderId}")
    public ResponseEntity<PaymentDto.PaymentAuditTimelineDto> getOrderAuditTimeline(
            @PathVariable String orderId) {
        return ResponseEntity.ok(paymentAuditService.getOrderAuditTimeline(orderId));
    }

    /**
     * Retrieve all historical payment orders and audit status for a specific user.
     */
    @GetMapping("/audit/user/{userId}")
    public ResponseEntity<List<PaymentDto.PaymentAuditTimelineDto>> getUserPaymentAuditHistory(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(paymentAuditService.getUserPaymentAuditHistory(userId));
    }

    /**
     * Manual support review and reconciliation endpoint.
     * Allows customer care to attach review notes, resolve disputes, and grant benefits.
     */
    @PostMapping("/audit/orders/{orderId}/review")
    public ResponseEntity<PaymentDto.PaymentAuditTimelineDto> reviewOrder(
            @AuthenticationPrincipal UUID adminUserId,
            @PathVariable String orderId,
            @RequestBody PaymentDto.ReviewOrderRequest request) {
        return ResponseEntity.ok(paymentAuditService.reviewAndReconcileOrder(orderId, request, adminUserId));
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return "UNKNOWN";
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "UNKNOWN";
    }

    private String extractUserAgent(HttpServletRequest request) {
        if (request == null) return "UNKNOWN";
        String ua = request.getHeader("User-Agent");
        String platform = request.getHeader("X-Client-Platform");
        if (platform != null) {
            return platform + (ua != null ? " (" + ua + ")" : "");
        }
        return ua != null ? ua : "UNKNOWN";
    }
}
