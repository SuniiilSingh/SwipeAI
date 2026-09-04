package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.PaymentDto;
import com.match.SwipeAI.service.integration.UpiPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UPI Sachet Monetization and Webhook Controller.
 * Provides micro-pricing (₹19–₹99) catalog, creates NPCI UPI deep-link orders,
 * and handles idempotent payment captured webhooks.
 */
@Slf4j
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentsController {

    private final UpiPaymentService upiPaymentService;

    /**
     * Retrieve the full micro-sachet catalog and Weekend Dating Pass pricing.
     *
     * @return List of catalog offerings with perks
     */
    @GetMapping("/store/catalog")
    public ResponseEntity<List<PaymentDto.CatalogItemDto>> getCatalog() {
        List<PaymentDto.CatalogItemDto> catalog = upiPaymentService.getCatalog();
        return ResponseEntity.ok(catalog);
    }

    /**
     * Initiate a micro-sachet transaction and generate an NPCI UPI deep-link intent string.
     *
     * @param userId Authenticated user UUID
     * @param request Selected SKU and optional VPA
     * @return UPI Intent URL (upi://pay?...) and QR code
     */
    @PostMapping("/upi/create-order")
    public ResponseEntity<PaymentDto.CreateOrderResponse> createOrder(
            @AuthenticationPrincipal UUID userId,
            @RequestBody PaymentDto.CreateOrderRequest request) {
        PaymentDto.CreateOrderResponse response = upiPaymentService.createOrder(userId, request.getSku(), request.getVpa());
        return ResponseEntity.ok(response);
    }

    /**
     * Idempotent payment webhook handler with HMAC-SHA256 signature verification.
     * Automatically credits user balances (Sparks, Boosts, Passes, DMs) upon capture.
     *
     * @param signature HMAC SHA-256 header (X-Razorpay-Signature)
     * @param idempotencyKey Transaction idempotency key (X-Idempotency-Key)
     * @param payload Webhook event payload
     * @return Processing status
     */
    @PostMapping("/upi/webhook")
    public ResponseEntity<Map<String, String>> handleUpiWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> payload) {
        log.info("Received UPI Webhook: {}", payload);
        boolean processed = upiPaymentService.processWebhook(signature, idempotencyKey, payload);
        return ResponseEntity.ok(Map.of(
                "status", processed ? "success" : "ignored",
                "message", processed ? "Payment captured and benefits credited" : "Order already processed or skipped"
        ));
    }

    /**
     * Demo simulation endpoint for developer testing without live payment webhooks.
     *
     * @param userId Authenticated user UUID
     * @param orderId Order ID to confirm
     * @return Confirmation status
     */
    @PostMapping("/upi/test-confirm/{orderId}")
    public ResponseEntity<Map<String, String>> testConfirmOrder(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String orderId) {
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
        boolean processed = upiPaymentService.processWebhook(null, "idem_" + orderId, mockPayload);
        return ResponseEntity.ok(Map.of("status", processed ? "success" : "failed", "orderId", orderId));
    }
}
