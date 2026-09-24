package com.match.SwipeAI.service.integration;

import com.match.SwipeAI.config.FeatureFlagsProperties;
import com.match.SwipeAI.dto.PaymentDto;
import com.match.SwipeAI.enums.OrderStatus;
import com.match.SwipeAI.enums.SkuType;
import com.match.SwipeAI.model.UpiOrder;
import com.match.SwipeAI.model.User;
import com.match.SwipeAI.repository.UpiOrderRepository;
import com.match.SwipeAI.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpiPaymentService {

    private final FeatureFlagsProperties properties;
    private final UpiOrderRepository upiOrderRepository;
    private final UserRepository userRepository;

    public List<PaymentDto.CatalogItemDto> getCatalog() {
        return List.of(
                PaymentDto.CatalogItemDto.builder()
                        .sku(SkuType.WEEKEND_PASS_99)
                        .title("Weekend Dating Pass")
                        .priceInr(99)
                        .directPriceInr(99)
                        .storePriceInr(129)
                        .googleProductId("blunderr_pass_weekend_129")
                        .appleProductId("com.blunderr.pass.weekend")
                        .subtitle("Unlimited Likes + 3 Sparks + See Who Liked You")
                        .tag("MOST POPULAR IN BENGALURU & DELHI")
                        .perks(List.of("Unlimited Daily Swipes", "3 Super Sparks Included", "Priority Profile Pool"))
                        .build(),
                PaymentDto.CatalogItemDto.builder()
                        .sku(SkuType.SUPER_SPARK_19)
                        .title("1 Super Spark")
                        .priceInr(19)
                        .directPriceInr(19)
                        .storePriceInr(29)
                        .googleProductId("blunderr_spark_29")
                        .appleProductId("com.blunderr.spark.29")
                        .subtitle("Stand out instantly with 3x reply rate")
                        .tag("SACHET")
                        .perks(List.of("Highlights your profile at top of feed"))
                        .build(),
                PaymentDto.CatalogItemDto.builder()
                        .sku(SkuType.CUTTING_CHAI_21)
                        .title("Virtual Cutting Chai Invite")
                        .priceInr(21)
                        .directPriceInr(21)
                        .storePriceInr(29)
                        .googleProductId("blunderr_chai_29")
                        .appleProductId("com.blunderr.chai.29")
                        .subtitle("Send a digital cutting chai + 15% partner cafe coupon")
                        .tag("HIGH REACTION")
                        .perks(List.of("100% of women say they reply to chai invites", "15% off Blue Tokai & Third Wave coupon"))
                        .build(),
                PaymentDto.CatalogItemDto.builder()
                        .sku(SkuType.BOOST_1X_FRIDAY_29)
                        .title("1 Friday Night Boost")
                        .priceInr(29)
                        .directPriceInr(29)
                        .storePriceInr(49)
                        .googleProductId("blunderr_boost_49")
                        .appleProductId("com.blunderr.boost.49")
                        .subtitle("10x profile visibility during 9 PM - 1 AM peak")
                        .tag("PEAK CONVERSION")
                        .perks(List.of("Surfaces profile to top of nearby candidates for 1 hour"))
                        .build(),
                PaymentDto.CatalogItemDto.builder()
                        .sku(SkuType.DIRECT_DMS_3X_49)
                        .title("3 Direct DMs")
                        .priceInr(49)
                        .directPriceInr(49)
                        .storePriceInr(69)
                        .googleProductId("blunderr_dms_69")
                        .appleProductId("com.blunderr.dms.69")
                        .subtitle("Skip the queue and message high-intent matches directly")
                        .tag("SACHET")
                        .perks(List.of("Send personalized intro before matching"))
                        .build(),
                PaymentDto.CatalogItemDto.builder()
                        .sku(SkuType.REVIVE_MATCH_19)
                        .title("Revive Expired Match")
                        .priceInr(19)
                        .directPriceInr(19)
                        .storePriceInr(29)
                        .googleProductId("blunderr_revive_29")
                        .appleProductId("com.blunderr.revive.29")
                        .subtitle("Unfreeze 48h timer and restore match")
                        .tag("SACHET")
                        .perks(List.of("Re-opens icebreaker chat lounge for 48 hours"))
                        .build(),
                PaymentDto.CatalogItemDto.builder()
                        .sku(SkuType.WEEKLY_PASS_149)
                        .title("Weekly VIP Pass")
                        .priceInr(149)
                        .directPriceInr(149)
                        .storePriceInr(199)
                        .googleProductId("blunderr_weekly_199")
                        .appleProductId("com.blunderr.weekly.199")
                        .subtitle("Full VIP access for 7 days with direct DMs")
                        .tag("POPULAR")
                        .perks(List.of("Unlimited likes", "5 Super Sparks", "3 Direct DMs"))
                        .build(),
                PaymentDto.CatalogItemDto.builder()
                        .sku(SkuType.FORTNIGHT_PASS_199)
                        .title("14-Day Fortnight Pass")
                        .priceInr(199)
                        .directPriceInr(199)
                        .storePriceInr(249)
                        .googleProductId("blunderr_fortnight_249")
                        .appleProductId("com.blunderr.fortnight.249")
                        .subtitle("Full VIP access for 14 days + 6 Sparks + 2 Boosts")
                        .tag("BEST VALUE")
                        .perks(List.of("Unlimited likes for 14 days", "6 Super Sparks", "2 Profile Boosts", "5 Direct DMs"))
                        .build(),
                PaymentDto.CatalogItemDto.builder()
                        .sku(SkuType.SELECT_QUARTERLY_999)
                        .title("Select Club (Quarterly)")
                        .priceInr(999)
                        .directPriceInr(999)
                        .storePriceInr(1299)
                        .googleProductId("blunderr_select_1299")
                        .appleProductId("com.blunderr.select.1299")
                        .subtitle("Concierge recommendations & priority DigiLocker pool")
                        .tag("PREMIUM")
                        .perks(List.of("Concierge curated dates", "Unlimited access", "Exclusive offline mixers"))
                        .build()
        );
    }

    @Transactional
    public PaymentDto.CreateOrderResponse createOrder(UUID userId, SkuType sku, String vpa) {
        String orderId = "order_sachet_" + UUID.randomUUID().toString().substring(0, 8);
        int amountPaise = sku.getAmountPaise();
        int amountInr = amountPaise / 100;

        // Construct standard NPCI UPI Deep Link intent string:
        // upi://pay?pa=merchant@upi&pn=SwipeAI&am=29&cu=INR&tn=FridayBoost&tr=order_id
        String merchantVpa = "payments@swipeai";
        String note = sku.name().replace("_", "");
        String upiIntentUrl = String.format("upi://pay?pa=%s&pn=SwipeAI&am=%d&cu=INR&tn=%s&tr=%s",
                merchantVpa, amountInr, note, orderId);

        UpiOrder order = UpiOrder.builder()
                .orderId(orderId)
                .userId(userId)
                .amountPaise(amountPaise)
                .currency("INR")
                .sku(sku)
                .vpa(vpa != null ? vpa : "user@okaxis")
                .status(OrderStatus.PENDING)
                .upiIntentUrl(upiIntentUrl)
                .build();

        upiOrderRepository.save(order);

        boolean razorpayEnabled = properties.getFeatures().getRazorpay().isEnabled();
        if (razorpayEnabled) {
            log.info("[FEATURE_FLAG: Razorpay LIVE] Creating live Razorpay order for user {} - Amount: ₹{}", userId, amountInr);
            /*
             * LIVE INTEGRATION SKELETON:
             * RazorpayClient client = new RazorpayClient(properties.getFeatures().getRazorpay().getKeyId(), properties.getFeatures().getRazorpay().getKeySecret());
             * JSONObject orderRequest = new JSONObject();
             * orderRequest.put("amount", amountPaise);
             * orderRequest.put("currency", "INR");
             * orderRequest.put("receipt", orderId);
             * orderRequest.put("notes", Map.of("userId", userId.toString(), "sku", sku.name()));
             * Order razorpayOrder = client.orders.create(orderRequest);
             * orderId = razorpayOrder.get("id");
             */
        } else {
            log.info("[MOCK TESTING ENVIRONMENT] Razorpay is disabled. Generated simulated UPI Intent: {} for user {}", upiIntentUrl, userId);
        }

        return PaymentDto.CreateOrderResponse.builder()
                .orderId(orderId)
                .sku(sku)
                .amountPaise(amountPaise)
                .formattedAmount("₹" + amountInr)
                .upiIntentUrl(upiIntentUrl)
                .qrCodeUrl("https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=" + upiIntentUrl)
                .razorpayKeyId(properties.getFeatures().getRazorpay().getKeyId())
                .simulated(!razorpayEnabled)
                .build();
    }

    @Transactional
    public boolean processWebhook(String signature, String idempotencyKey, Map<String, Object> webhookPayload) {
        log.info("Processing UPI webhook with idempotencyKey: {}", idempotencyKey);

        boolean razorpayEnabled = properties.getFeatures().getRazorpay().isEnabled();
        if (razorpayEnabled) {
            String webhookSecret = properties.getFeatures().getRazorpay().getWebhookSecret();
            if (!verifyHmacSignature(webhookPayload.toString(), signature, webhookSecret)) {
                log.error("Invalid HMAC signature on UPI webhook");
                return false;
            }
        }

        Map<String, Object> payloadMap = (Map<String, Object>) webhookPayload.get("payload");
        if (payloadMap == null) {
            return false;
        }

        Map<String, Object> paymentMap = (Map<String, Object>) payloadMap.get("payment");
        if (paymentMap == null) {
            return false;
        }

        String orderId = (String) paymentMap.get("order_id");
        String paymentId = (String) paymentMap.get("id");
        String status = (String) paymentMap.get("status");

        Optional<UpiOrder> optionalOrder = upiOrderRepository.findByOrderId(orderId);
        if (optionalOrder.isEmpty()) {
            log.warn("Order not found for orderId: {}", orderId);
            return false;
        }

        UpiOrder order = optionalOrder.get();
        // Idempotency check: if already captured, return immediately
        if (order.getStatus() == OrderStatus.CAPTURED) {
            log.info("Order {} already CAPTURED, skipping duplicate event", orderId);
            return true;
        }

        if ("captured".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status)) {
            order.setStatus(OrderStatus.CAPTURED);
            order.setPaymentId(paymentId != null ? paymentId : "pay_" + UUID.randomUUID().toString().substring(0, 8));
            upiOrderRepository.save(order);

            // Credit benefits to user
            grantUserBenefits(order.getUserId(), order.getSku());
            log.info("Successfully captured UPI payment for order {}, granted SKU {}", orderId, order.getSku());
            return true;
        } else {
            order.setStatus(OrderStatus.FAILED);
            upiOrderRepository.save(order);
            return false;
        }
    }

    @Transactional
    public void grantUserBenefits(UUID userId, SkuType sku) {
        userRepository.findById(userId).ifPresent(user -> {
            switch (sku) {
                case BOOST_1X_FRIDAY_29:
                    user.setBoostsBalance(user.getBoostsBalance() + 1);
                    break;
                case DIRECT_DMS_3X_49:
                    user.setDirectDmsBalance(user.getDirectDmsBalance() + 3);
                    break;
                case SUPER_SPARK_19:
                    user.setSparksBalance(user.getSparksBalance() + 1);
                    break;
                case WEEKEND_PASS_99:
                    user.setHasActivePass(true);
                    user.setPassExpiry(OffsetDateTime.now().plusDays(3));
                    user.setSparksBalance(user.getSparksBalance() + 3);
                    break;
                case WEEKLY_PASS_149:
                    user.setHasActivePass(true);
                    user.setPassExpiry(OffsetDateTime.now().plusDays(7));
                    user.setSparksBalance(user.getSparksBalance() + 5);
                    user.setDirectDmsBalance(user.getDirectDmsBalance() + 3);
                    break;
                case FORTNIGHT_PASS_199:
                    user.setHasActivePass(true);
                    user.setPassExpiry(OffsetDateTime.now().plusDays(14));
                    user.setSparksBalance(user.getSparksBalance() + 6);
                    user.setBoostsBalance(user.getBoostsBalance() + 2);
                    user.setDirectDmsBalance(user.getDirectDmsBalance() + 5);
                    break;
                case SELECT_QUARTERLY_999:
                    user.setHasActivePass(true);
                    user.setPassExpiry(OffsetDateTime.now().plusDays(90));
                    user.setSparksBalance(user.getSparksBalance() + 25);
                    user.setDirectDmsBalance(user.getDirectDmsBalance() + 25);
                    user.setKarmaScore(Math.min(200, user.getKarmaScore() + 20));
                    break;
                case CUTTING_CHAI_21:
                    user.setSparksBalance(user.getSparksBalance() + 1);
                    break;
                case REVIVE_MATCH_19:
                    // Handled in match revive
                    break;
            }
            userRepository.save(user);
        });
    }

    private boolean verifyHmacSignature(String payload, String expectedSignature, String secret) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().equalsIgnoreCase(expectedSignature);
        } catch (Exception e) {
            return false;
        }
    }
}
