package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.PaymentDto;
import com.match.SwipeAI.model.User;
import com.match.SwipeAI.repository.PaymentExecutionLogRepository;
import com.match.SwipeAI.repository.UserRepository;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@CrossOrigin
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentsController {

    private final UpiPaymentService upiPaymentService;
    private final IapVerificationService iapVerificationService;
    private final CashfreePaymentService cashfreePaymentService;
    private final PaymentAuditService paymentAuditService;
    private final PaymentCryptoService paymentCryptoService;
    private final com.match.SwipeAI.service.integration.PaymentExecutionLogService paymentExecutionLogService;
    private final UserRepository userRepository;
    private final com.match.SwipeAI.repository.ProfileRepository profileRepository;
    private final PaymentExecutionLogRepository paymentExecutionLogRepository;
    private final com.match.SwipeAI.repository.UpiOrderRepository upiOrderRepository;

    /**
     * Retrieve active membership plan, credit balances, and order history for the current user.
     */
    @GetMapping("/active-plan")
    public ResponseEntity<PaymentDto.ActivePlanResponse> getActivePlan(@AuthenticationPrincipal UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        boolean hasPass = Boolean.TRUE.equals(user.getHasActivePass());
        java.time.OffsetDateTime expiry = user.getPassExpiry();
        String planName = hasPass ? "VIP Pass (Active)" : "Free Plan";
        String planStatus = hasPass ? "ACTIVE" : "FREE";
        String passExpiryStr = "No Active Pass";
        Long daysLeft = 0L;
        String validUntilStr = "N/A";

        if (hasPass || (expiry != null && expiry.isAfter(java.time.OffsetDateTime.now()))) {
            if (expiry != null && expiry.isAfter(java.time.OffsetDateTime.now())) {
                daysLeft = Math.max(1L, java.time.Duration.between(java.time.OffsetDateTime.now(), expiry).toDays());
                validUntilStr = expiry.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"));
                passExpiryStr = "Valid until " + validUntilStr + " (" + daysLeft + " days left)";
            } else {
                daysLeft = 7L;
                validUntilStr = java.time.OffsetDateTime.now().plusDays(7).format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"));
                passExpiryStr = "VIP Pass Active (7 days left)";
            }
        }

        // Fetch real UPI Orders first for accurate amounts
        List<com.match.SwipeAI.model.UpiOrder> upiOrders = upiOrderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<PaymentDto.TransactionHistoryItemDto> history;

        if (!upiOrders.isEmpty()) {
            history = upiOrders.stream()
                    .limit(15)
                    .map(order -> {
                        int priceInr = (order.getAmountPaise() != null && order.getAmountPaise() > 0)
                                ? (order.getAmountPaise() / 100)
                                : 79;
                        String title = order.getSku() != null ? order.getSku().name().replace('_', ' ') : "Store Purchase";
                        String dateStr = order.getCreatedAt() != null
                                ? order.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                : "Recent";
                        return PaymentDto.TransactionHistoryItemDto.builder()
                                .orderId(order.getOrderId() != null ? order.getOrderId() : "TRX-" + order.getId().toString().substring(0, 8))
                                .title(title)
                                .amountFormatted("₹" + priceInr)
                                .date(dateStr)
                                .status(order.getStatus() != null ? order.getStatus().name() : "SUCCESS")
                                .provider("UPI")
                                .build();
                    })
                    .toList();
        } else {
            history = paymentExecutionLogRepository
                    .findByUserIdOrderByCreatedAtDesc(userId)
                    .stream()
                    .limit(15)
                    .<PaymentDto.TransactionHistoryItemDto>map(log -> {
                        String summary = log.getSummary() != null ? log.getSummary() : "";
                        String amtStr = "₹79";
                        if (summary.contains("₹")) {
                            int idx = summary.indexOf("₹");
                            int spaceIdx = summary.indexOf(" ", idx);
                            amtStr = spaceIdx > idx ? summary.substring(idx, spaceIdx) : summary.substring(idx);
                        } else if (summary.contains("_")) {
                            String[] parts = summary.split("_");
                            try {
                                int val = Integer.parseInt(parts[parts.length - 1]);
                                amtStr = "₹" + val;
                            } catch (Exception ignored) {}
                        }
                        return PaymentDto.TransactionHistoryItemDto.builder()
                                .orderId(log.getOrderId() != null ? log.getOrderId() : "TRX-" + log.getId().toString().substring(0, 8))
                                .title(log.getSummary() != null ? log.getSummary() : (log.getAction() != null ? log.getAction() : "Store Purchase"))
                                .amountFormatted(amtStr)
                                .date(log.getCreatedAt() != null ? log.getCreatedAt().toString().substring(0, 10) : "Recent")
                                .status(log.getStatus() != null ? log.getStatus() : "COMPLETED")
                                .provider("STORE")
                                .build();
                    })
                    .toList();
        }

        PaymentDto.ActivePlanResponse response = PaymentDto.ActivePlanResponse.builder()
                .activePlanName(planName)
                .planStatus(planStatus)
                .sparksBalance(user.getSparksBalance() != null ? user.getSparksBalance() : 0)
                .boostsBalance(user.getBoostsBalance() != null ? user.getBoostsBalance() : 0)
                .directDmsBalance(user.getDirectDmsBalance() != null ? user.getDirectDmsBalance() : 0)
                .hasActivePass(hasPass)
                .passExpiryDate(passExpiryStr)
                .passExpiryDaysLeft(daysLeft)
                .passValidUntil(validUntilStr)
                .recentTransactions(history)
                .build();

        return ResponseEntity.ok(response);
    }

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
    @com.match.SwipeAI.annotation.TrackPaymentTransaction(action = "CREATE_UPI_ORDER")
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
    @com.match.SwipeAI.annotation.TrackPaymentTransaction(action = "UPI_WEBHOOK")
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
    @com.match.SwipeAI.annotation.TrackPaymentTransaction(action = "CONFIRM_UPI_ORDER")
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
     * Confirm Direct SBI UPI Payment (yb.8120969849@sbi / Bharat Generic Enterprises).
     * Accepts orderId and client-side bank transaction reference/UTR.
     */
    @com.match.SwipeAI.annotation.TrackPaymentTransaction(action = "CONFIRM_DIRECT_UPI")
    @PostMapping("/upi/confirm-direct")
    public ResponseEntity<Map<String, Object>> confirmDirectUpi(
            @AuthenticationPrincipal UUID userId,
            @RequestBody PaymentDto.ConfirmDirectUpiRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);

        boolean processed = upiPaymentService.confirmDirectUpiPayment(
                request.getOrderId(), request.getUtr(), userId, clientIp, userAgent);

        return ResponseEntity.ok(Map.of(
                "status", processed ? "success" : "failed",
                "orderId", request.getOrderId() != null ? request.getOrderId() : "",
                "message", processed ? "Payment confirmed and benefits activated!" : "Order already processed or invalid"
        ));
    }

    /**
     * Verify and credit Native In-App Purchases (Google Play Billing / Apple StoreKit).
     */
    @com.match.SwipeAI.annotation.TrackPaymentTransaction(action = "IAP_VERIFY")
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
    @com.match.SwipeAI.annotation.TrackPaymentTransaction(action = "CREATE_CASHFREE_ORDER")
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
    @com.match.SwipeAI.annotation.TrackPaymentTransaction(action = "CASHFREE_WEBHOOK")
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
    @com.match.SwipeAI.annotation.TrackPaymentTransaction(action = "CONFIRM_CASHFREE_ORDER")
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
    @com.match.SwipeAI.annotation.TrackPaymentTransaction(action = "MANUAL_ORDER_REVIEW")
    @PostMapping("/audit/orders/{orderId}/review")
    public ResponseEntity<PaymentDto.PaymentAuditTimelineDto> reviewOrder(
            @AuthenticationPrincipal UUID adminUserId,
            @PathVariable String orderId,
            @RequestBody PaymentDto.ReviewOrderRequest request) {
        return ResponseEntity.ok(paymentAuditService.reviewAndReconcileOrder(orderId, request, adminUserId));
    }

    /**
     * Retrieve lightweight AOP execution logs for support diagnosis.
     */
    @GetMapping("/audit/execution-logs")
    public ResponseEntity<List<com.match.SwipeAI.model.PaymentExecutionLog>> getExecutionLogs(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(paymentExecutionLogService.getRecentLogs(status));
    }

    /**
     * Retrieve lightweight AOP execution logs for a specific order.
     */
    @GetMapping("/audit/execution-logs/{orderId}")
    public ResponseEntity<List<com.match.SwipeAI.model.PaymentExecutionLog>> getExecutionLogsByOrderId(
            @PathVariable String orderId) {
        return ResponseEntity.ok(paymentExecutionLogService.getLogsByOrderId(orderId));
    }

    /**
     * Web Checkout: Look up BlunderR profile by mobile phone number.
     */
    @GetMapping("/web/lookup-phone")
    public ResponseEntity<Map<String, Object>> lookupPhone(@RequestParam String phone) {
        String normalizedPhone = normalizePhone(phone);
        User user = userRepository.findByPhoneE164(normalizedPhone).orElse(null);
        if (user == null && !normalizedPhone.startsWith("+91") && normalizedPhone.length() == 10) {
            user = userRepository.findByPhoneE164("+91" + normalizedPhone).orElse(null);
        }

        if (user == null) {
            return ResponseEntity.ok(Map.of(
                    "found", false,
                    "phone", normalizedPhone,
                    "message", "Phone number not registered on BlunderR yet."
            ));
        }

        com.match.SwipeAI.model.Profile profile = profileRepository.findById(user.getId()).orElse(null);
        String displayName = (profile != null && profile.getDisplayName() != null && !profile.getDisplayName().isBlank())
                ? profile.getDisplayName()
                : "BlunderR Member";
        String photo = (profile != null && profile.getPhoto1() != null) ? profile.getPhoto1() : "";
        String city = (profile != null && profile.getCity() != null) ? profile.getCity() : "";
        boolean isFaceVerified = Boolean.TRUE.equals(user.getFaceVerified());

        Map<String, Object> res = new java.util.LinkedHashMap<>();
        res.put("found", true);
        res.put("userId", user.getId().toString());
        res.put("phone", user.getPhoneE164());
        res.put("displayName", displayName);
        res.put("photo", photo);
        res.put("city", city);
        res.put("faceVerified", isFaceVerified);
        res.put("hasActivePass", Boolean.TRUE.equals(user.getHasActivePass()));
        res.put("passExpiry", user.getPassExpiry() != null ? user.getPassExpiry().toString() : "");
        res.put("sparksBalance", user.getSparksBalance() != null ? user.getSparksBalance() : 0);
        res.put("boostsBalance", user.getBoostsBalance() != null ? user.getBoostsBalance() : 0);

        return ResponseEntity.ok(res);
    }

    /**
     * Web Checkout: Create direct UPI order for a phone number or userId.
     */
    @com.match.SwipeAI.annotation.TrackPaymentTransaction(action = "CREATE_WEB_UPI_ORDER")
    @PostMapping("/web/create-order")
    public ResponseEntity<PaymentDto.CreateOrderResponse> createWebOrder(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        String phone = request.get("phone");
        String skuStr = request.get("sku");
        if (skuStr == null || skuStr.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        com.match.SwipeAI.enums.SkuType sku;
        try {
            sku = com.match.SwipeAI.enums.SkuType.valueOf(skuStr.trim());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        UUID userId = null;
        if (request.containsKey("userId") && request.get("userId") != null && !request.get("userId").isBlank()) {
            try {
                userId = UUID.fromString(request.get("userId").trim());
            } catch (Exception ignored) {}
        }

        if (userId == null && phone != null && !phone.isBlank()) {
            String normalizedPhone = normalizePhone(phone);
            User user = userRepository.findByPhoneE164(normalizedPhone).orElse(null);
            if (user == null && !normalizedPhone.startsWith("+91") && normalizedPhone.length() == 10) {
                user = userRepository.findByPhoneE164("+91" + normalizedPhone).orElse(null);
            }
            if (user != null) {
                userId = user.getId();
            }
        }

        if (userId == null) {
            userId = UUID.randomUUID();
        }

        String clientIp = extractClientIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);
        PaymentDto.CreateOrderResponse response = upiPaymentService.createOrder(userId, sku, null, clientIp, userAgent);
        return ResponseEntity.ok(response);
    }

    /**
     * Web Checkout: Confirm Direct SBI UPI Payment from the website.
     */
    @com.match.SwipeAI.annotation.TrackPaymentTransaction(action = "CONFIRM_WEB_UPI_ORDER")
    @PostMapping("/web/confirm-order")
    public ResponseEntity<Map<String, Object>> confirmWebOrder(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        String orderId = request.get("orderId");
        String utr = request.get("utr");
        String phone = request.get("phone");
        if (orderId == null || orderId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "failed", "message", "Missing orderId"));
        }

        String clientIp = extractClientIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);

        var optionalOrder = upiOrderRepository.findByOrderId(orderId);
        if (optionalOrder.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "failed", "message", "Order not found"));
        }

        var order = optionalOrder.get();
        if (phone != null && !phone.isBlank()) {
            String normalizedPhone = normalizePhone(phone);
            User user = userRepository.findByPhoneE164(normalizedPhone).orElse(null);
            if (user == null && !normalizedPhone.startsWith("+91") && normalizedPhone.length() == 10) {
                user = userRepository.findByPhoneE164("+91" + normalizedPhone).orElse(null);
            }
            if (user != null && !user.getId().equals(order.getUserId())) {
                order.setUserId(user.getId());
                upiOrderRepository.save(order);
            }
        }

        boolean processed = upiPaymentService.confirmDirectUpiPayment(orderId, utr, order.getUserId(), clientIp, userAgent);
        return ResponseEntity.ok(Map.of(
                "status", processed ? "success" : "failed",
                "orderId", orderId,
                "message", processed ? "Payment confirmed and VIP benefits activated!" : "Transaction reference is invalid, expired, or already redeemed"
        ));
    }

    /**
     * Web Checkout: Poll order status by orderId.
     */
    @GetMapping("/web/order-status/{orderId}")
    public ResponseEntity<Map<String, Object>> getWebOrderStatus(@PathVariable String orderId) {
        var optionalOrder = upiOrderRepository.findByOrderId(orderId);
        if (optionalOrder.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var order = optionalOrder.get();
        return ResponseEntity.ok(Map.of(
                "orderId", order.getOrderId(),
                "status", order.getStatus().name(),
                "sku", order.getSku() != null ? order.getSku().name() : "",
                "amountInr", order.getAmountPaise() != null ? order.getAmountPaise() / 100 : 0
        ));
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String cleaned = phone.replaceAll("[^0-9+]", "");
        if (cleaned.startsWith("+")) return cleaned;
        if (cleaned.startsWith("91") && cleaned.length() == 12) return "+" + cleaned;
        if (cleaned.length() == 10) return "+91" + cleaned;
        return cleaned;
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
