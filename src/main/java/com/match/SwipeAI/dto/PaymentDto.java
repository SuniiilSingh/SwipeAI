package com.match.SwipeAI.dto;

import com.match.SwipeAI.enums.OrderStatus;
import com.match.SwipeAI.enums.SkuType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Objects for UPI Sachet Payments and Webhooks.
 */
public class PaymentDto {

    /**
     * Request payload to initiate a UPI micro-sachet transaction.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrderRequest {
        private SkuType sku;
        private String vpa;
    }

    /**
     * Created order response containing deep-link NPCI URI and QR code.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrderResponse {
        private String orderId;
        private SkuType sku;
        private int amountPaise;
        private String formattedAmount;
        private String upiIntentUrl;
        private String qrCodeUrl;
        private String razorpayKeyId;
        private boolean simulated;
    }

    /**
     * Incoming payment gateway webhook payload.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RazorpayWebhookPayload {
        private String event;
        private Map<String, Object> payload;
    }

    /**
     * Store catalog item.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatalogItemDto {
        private SkuType sku;
        private String title;
        private int priceInr;
        private int storePriceInr;
        private int directPriceInr;
        private String googleProductId;
        private String appleProductId;
        private String subtitle;
        private String tag;
        private List<String> perks;
    }

    /**
     * In-App Purchase Verification Request (Google Play / Apple StoreKit).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IapVerifyRequest {
        private String platform; // "android" or "ios"
        private String productId;
        private String purchaseToken;
        private String orderId;
        private SkuType sku;
    }

    /**
     * In-App Purchase Verification Response.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IapVerifyResponse {
        private boolean success;
        private SkuType sku;
        private String orderId;
        private String message;
        private Map<String, Object> perksGranted;
    }

    /**
     * Request payload to initiate a Cashfree checkout order.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashfreeCreateOrderRequest {
        private SkuType sku;
        private String customerPhone;
    }

    /**
     * Created Cashfree order response with payment session ID.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashfreeCreateOrderResponse {
        private String orderId;
        private String paymentSessionId;
        private String cfOrderId;
        private int orderAmount;
        private String orderCurrency;
        private SkuType sku;
        private boolean simulated;
    }
}
