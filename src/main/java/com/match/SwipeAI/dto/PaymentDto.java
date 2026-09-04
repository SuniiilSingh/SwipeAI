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
        private String subtitle;
        private String tag;
        private List<String> perks;
    }
}
