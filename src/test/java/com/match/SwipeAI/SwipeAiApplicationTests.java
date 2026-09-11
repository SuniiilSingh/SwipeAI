package com.match.SwipeAI;

import com.match.SwipeAI.dto.KycDto;
import com.match.SwipeAI.dto.MatchDto;
import com.match.SwipeAI.dto.PaymentDto;
import com.match.SwipeAI.dto.ShieldDto;
import com.match.SwipeAI.enums.*;
import com.match.SwipeAI.model.*;
import com.match.SwipeAI.service.engine.IcebreakerEngine;
import com.match.SwipeAI.service.engine.MultiObjectiveMatchEngine;
import com.match.SwipeAI.service.engine.ShadowShieldService;
import com.match.SwipeAI.service.integration.DigiLockerKycService;
import com.match.SwipeAI.service.integration.OtpService;
import com.match.SwipeAI.service.integration.UpiPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:swipe_testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.features.twilio.enabled=false"
})
class SwipeAiApplicationTests {

    @Autowired
    private OtpService otpService;

    @Autowired
    private DigiLockerKycService digiLockerKycService;

    @Autowired
    private ShadowShieldService shadowShieldService;

    @Autowired
    private MultiObjectiveMatchEngine matchEngine;

    @Autowired
    private UpiPaymentService upiPaymentService;

    @Autowired
    private IcebreakerEngine icebreakerEngine;

    @Autowired
    private com.match.SwipeAI.service.integration.R2StorageService r2StorageService;

    @Test
    void contextLoads() {
        assertNotNull(otpService);
        assertNotNull(digiLockerKycService);
        assertNotNull(shadowShieldService);
        assertNotNull(matchEngine);
        assertNotNull(upiPaymentService);
        assertNotNull(r2StorageService);
    }

    @Test
    void testR2StorageService_UploadAndPresign() {
        // Direct upload test
        byte[] sampleData = "sample image data bytes".getBytes();
        Map<String, String> uploadResult = r2StorageService.uploadFile("avatar.jpg", "image/jpeg", sampleData);
        assertNotNull(uploadResult.get("fileId"));
        assertNotNull(uploadResult.get("publicUrl"));
        assertTrue(uploadResult.get("publicUrl").contains(uploadResult.get("fileId")));

        // Presigned URL test
        Map<String, String> presignResult = r2StorageService.generatePresignedPutUrl("avatar.jpg", "image/jpeg");
        assertNotNull(presignResult.get("fileId"));
        assertNotNull(presignResult.get("uploadUrl"));
        assertNotNull(presignResult.get("publicUrl"));

        // Cleanup
        r2StorageService.deleteFile(uploadResult.get("fileId"));
    }

    @Test
    void testOtpService_RequiresTwilioVerifyConfiguration() {
        String phone = "+919876543210";
        // With empty OTP, returns false
        assertFalse(otpService.verifyOtp(phone, ""));
        assertFalse(otpService.verifyOtp(phone, null));

        // Unapproved / invalid OTP returns false without silent fallback
        assertFalse(otpService.verifyOtp(phone, "1234"));
        assertFalse(otpService.verifyOtp(phone, "9999"));
    }

    @Test
    void testDigiLockerKyc_ZeroKnowledgeVerification() {
        UUID userId = UUID.randomUUID();
        KycDto.DigiLockerInitiateResponse initRes = digiLockerKycService.initiateKyc(userId);
        assertNotNull(initRes.getStateToken());

        KycDto.DigiLockerProofRequest proofReq = new KycDto.DigiLockerProofRequest(initRes.getStateToken(), "code_123", true);
        KycDto.DigiLockerProofResponse proofRes = digiLockerKycService.verifyProof(userId, proofReq);

        assertTrue(proofRes.isVerified());
        assertTrue(proofRes.isAdult());
        assertEquals("GOLD_SHIELD", proofRes.getBadge());
    }

    @Test
    void testShadowShield_ContactAndCorporateDomainFiltering() {
        UUID viewerId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        String rawPhone = "+919876512345";
        String hash = shadowShieldService.hashPhoneNumber(rawPhone);
        assertNotNull(hash);
        assertEquals(64, hash.length()); // SHA-256 length

        // Set Corporate Domain Shield
        ShieldDto.ShieldStatusResponse domainRes = shadowShieldService.setCorporateDomain(viewerId, "swiggy.in");
        assertEquals("swiggy.in", domainRes.getCorporateDomain());
        assertTrue(domainRes.isShieldActive());
    }

    @Test
    void testMultiObjectiveMatchEngine_FormulaCalculation() {
        User userA = User.builder().id(UUID.randomUUID()).karmaScore(150).latitude(12.9716).longitude(77.5946).build();
        Profile profA = Profile.builder().dietaryPref(DietaryPreference.PURE_VEG).livingStatus(LivingStatus.INDEPENDENT_FLAT).languagesSpoken(List.of("English", "Hindi")).zodiacSign("Leo").build();

        User userB = User.builder().id(UUID.randomUUID()).karmaScore(145).latitude(12.9784).longitude(77.6408).build();
        Profile profB = Profile.builder().dietaryPref(DietaryPreference.PURE_VEG).livingStatus(LivingStatus.INDEPENDENT_FLAT).languagesSpoken(List.of("English", "Hindi")).zodiacSign("Aries").build();

        double dist = matchEngine.calculateDistanceKm(userA.getLatitude(), userA.getLongitude(), userB.getLatitude(), userB.getLongitude());
        assertTrue(dist > 0.0);

        int score = matchEngine.calculateCompatibilityScore(userA, profA, userB, profB, dist);
        assertTrue(score >= 50 && score <= 99);
    }

    @Test
    void testUpiSachetStore_OrderCreationAndWebhookProcessing() {
        UUID userId = UUID.randomUUID();

        // 1. Create Sachet Order for ₹29 Friday Boost
        PaymentDto.CreateOrderResponse order = upiPaymentService.createOrder(userId, SkuType.BOOST_1X_FRIDAY_29, "user@okaxis");
        assertNotNull(order.getOrderId());
        assertEquals("₹29", order.getFormattedAmount());
        assertTrue(order.getUpiIntentUrl().startsWith("upi://pay"));

        // 2. Idempotent Webhook Simulation
        Map<String, Object> webhookPayload = Map.of(
                "event", "payment.captured",
                "payload", Map.of(
                        "payment", Map.of(
                                "id", "pay_test_123",
                                "order_id", order.getOrderId(),
                                "status", "captured",
                                "method", "upi"
                        )
                )
        );

        boolean processed = upiPaymentService.processWebhook(null, "idem_key_1", webhookPayload);
        assertTrue(processed);

        // Duplicate webhook call should be idempotent (return true without duplicate granting)
        boolean duplicateProcessed = upiPaymentService.processWebhook(null, "idem_key_1", webhookPayload);
        assertTrue(duplicateProcessed);

        // 3. Test ₹199 14-Day Fortnight Pass order creation
        PaymentDto.CreateOrderResponse fortnightOrder = upiPaymentService.createOrder(userId, SkuType.FORTNIGHT_PASS_199, "user@okaxis");
        assertNotNull(fortnightOrder.getOrderId());
        assertEquals("₹199", fortnightOrder.getFormattedAmount());
        assertEquals(19900, fortnightOrder.getAmountPaise());
        assertTrue(fortnightOrder.getUpiIntentUrl().contains("am=199"));
    }

    @Test
    void testIcebreakerEngine_QuizSerialization() {
        MatchDto.IcebreakerQuizDto initialQuiz = icebreakerEngine.createInitialQuiz();
        assertNotNull(initialQuiz.getQuestion());
        assertEquals(3, initialQuiz.getOptions().size());

        String serialized = icebreakerEngine.serializeQuizData(initialQuiz);
        MatchDto.IcebreakerQuizDto parsed = icebreakerEngine.parseQuizData(serialized);

        assertEquals(initialQuiz.getQuizId(), parsed.getQuizId());
        assertEquals(initialQuiz.getQuestion(), parsed.getQuestion());
    }
}
