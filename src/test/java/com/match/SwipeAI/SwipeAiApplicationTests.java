package com.match.SwipeAI;

import com.match.SwipeAI.dto.AuthDto;
import com.match.SwipeAI.dto.KycDto;
import com.match.SwipeAI.dto.MatchDto;
import com.match.SwipeAI.dto.PaymentDto;
import com.match.SwipeAI.dto.ProfileDto;
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

import java.time.LocalDate;
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
    private com.match.SwipeAI.service.engine.MutualChemistrySparksEngine mutualChemistrySparksEngine;

    @Autowired
    private com.match.SwipeAI.service.ProfileService profileService;

    @Autowired
    private com.match.SwipeAI.service.AuthService authService;

    @Autowired
    private com.match.SwipeAI.repository.UserRepository userRepository;

    @Autowired
    private com.match.SwipeAI.repository.ProfileRepository profileRepository;

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

        // When mock OTP is enabled, 123456 is approved and sendOtp bypasses Twilio
        assertEquals("MOCK_OTP_SENT", otpService.sendOtp(phone, "sms"));
        assertTrue(otpService.verifyOtp(phone, "123456"));
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

    @Test
    void testMutualChemistrySparksEngine_SharedInterestsAndDiet() {
        Profile viewer = Profile.builder()
                .displayName("Sunil")
                .interests("Specialty Coffee, Cycling, Stand-up comedy")
                .dietaryPref(DietaryPreference.PURE_VEG)
                .livingStatus(LivingStatus.INDEPENDENT_FLAT)
                .build();

        Profile candidate = Profile.builder()
                .displayName("Ananya")
                .interests("Specialty Coffee, Photography, Trekking")
                .dietaryPref(DietaryPreference.PURE_VEG)
                .livingStatus(LivingStatus.INDEPENDENT_FLAT)
                .occupation("Product Designer")
                .company("Cred")
                .city("Bengaluru")
                .build();

        List<String> sparks = mutualChemistrySparksEngine.generateMutualSparks(viewer, candidate, null);
        assertNotNull(sparks);
        assertEquals(3, sparks.size());

        // Should include coffee spark and veggie match spark
        boolean hasCoffee = sparks.stream().anyMatch(s -> s.contains("coffee") || s.contains("Coffee"));
        boolean hasVeggie = sparks.stream().anyMatch(s -> s.contains("Veggie") || s.contains("Pure Veg"));
        assertTrue(hasCoffee, "Sparks should highlight mutual specialty coffee");
        assertTrue(hasVeggie, "Sparks should highlight mutual Pure Veg lifestyle");
    }

    @Test
    void testMutualChemistrySparksEngine_QuizConsensus() {
        Profile viewer = Profile.builder().displayName("Sunil").build();
        Profile candidate = Profile.builder().displayName("Ananya").build();

        MatchDto.IcebreakerQuizDto quiz = MatchDto.IcebreakerQuizDto.builder()
                .options(List.of("Filter Coffee & Dosa crawl in Indiranagar", "Sleep until 2 PM", "Road trip to Nandi Hills"))
                .userAAnswer(0)
                .userBAnswer(0)
                .isCompleted(true)
                .isMutualAgreement(true)
                .build();

        List<String> sparks = mutualChemistrySparksEngine.generateMutualSparks(viewer, candidate, quiz);
        assertNotNull(sparks);
        assertEquals(3, sparks.size());

        boolean hasQuizSpark = sparks.stream().anyMatch(s -> s.contains("100% agreement on the Sunday Dosa crawl"));
        assertTrue(hasQuizSpark, "Sparks should celebrate 100% quiz agreement");
    }

    @Test
    void testProfileCompletionPercentage_Baseline30PercentAndScaling() {
        User user = User.builder().gender(Gender.MALE).build();
        Profile basicProfile = Profile.builder()
                .displayName("Sunil")
                .sexualOrientation("Straight")
                .build();

        // Exactly Name (10) + Gender (10) + Orientation (10) = 30%
        int baselinePct = profileService.calculateCompletionPercentage(user, basicProfile);
        assertEquals(30, baselinePct);

        // Incomplete profile missing orientation
        Profile incompleteProfile = Profile.builder()
                .displayName("Sunil")
                .build();
        int incompletePct = profileService.calculateCompletionPercentage(user, incompleteProfile);
        assertEquals(20, incompletePct);
        assertTrue(incompletePct < 30);
    }

    @Test
    void testProfilePhotoDeletion_ClearsSlotAndPhotosJson() {
        User user = userRepository.save(User.builder()
                .phoneE164("+919999988888")
                .gender(Gender.MALE)
                .build());

        Profile profile = profileRepository.save(Profile.builder()
                .userId(user.getId())
                .displayName("Photo Tester")
                .photo1("https://example.com/photo1.jpg")
                .photo2("https://example.com/photo2.jpg")
                .photosJson("[\"https://example.com/photo1.jpg\",\"https://example.com/photo2.jpg\"]")
                .build());

        // 1. Delete Photo 2
        ProfileDto.ProfileRequest delRequest = new ProfileDto.ProfileRequest();
        delRequest.setPhoto2("");
        delRequest.setPhotos(List.of("https://example.com/photo1.jpg"));

        ProfileDto.ProfileResponse res1 = profileService.updateProfile(user.getId(), delRequest);
        assertNull(res1.getPhoto2(), "Photo2 should be null after deletion");
        assertEquals(1, res1.getPhotos().size());
        assertEquals("https://example.com/photo1.jpg", res1.getPhotos().get(0));

        // 2. Delete Photo 1 (All photos deleted)
        ProfileDto.ProfileRequest delAllRequest = new ProfileDto.ProfileRequest();
        delAllRequest.setPhoto1("");
        delAllRequest.setPhotos(List.of());

        ProfileDto.ProfileResponse res2 = profileService.updateProfile(user.getId(), delAllRequest);
        assertNull(res2.getPhoto1(), "Photo1 should be null after deletion");
        assertTrue(res2.getPhotos().isEmpty(), "Photos list should be empty when all photos are deleted");
    }

    @Test
    void testLocationAccessAndPersistence_ProfileAndUpdateLocation() {
        String phone = "+919876543210";
        userRepository.findByPhoneE164(phone).ifPresent(u -> {
            profileRepository.deleteById(u.getId());
            userRepository.delete(u);
        });

        User user = userRepository.save(User.builder()
                .phoneE164(phone)
                .gender(Gender.MALE)
                .intent(DatingIntent.SERIOUS_DATING)
                .birthDate(LocalDate.of(1998, 5, 20))
                .latitude(12.9716)
                .longitude(77.5946)
                .build());

        profileRepository.save(Profile.builder()
                .userId(user.getId())
                .displayName("Location Tester")
                .build());

        // 1. Fetch ProfileResponse and verify coordinates are returned
        ProfileDto.ProfileResponse profileRes = profileService.getProfile(user.getId());
        assertEquals(12.9716, profileRes.getLatitude());
        assertEquals(77.5946, profileRes.getLongitude());

        // 2. Test updating location directly via updateLocation
        ProfileDto.ProfileResponse updatedLocRes = profileService.updateLocation(user.getId(), 19.0760, 72.8777);
        assertEquals(19.0760, updatedLocRes.getLatitude());
        assertEquals(72.8777, updatedLocRes.getLongitude());

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(19.0760, updatedUser.getLatitude());
        assertEquals(72.8777, updatedUser.getLongitude());

        // 3. Test updating location via updateProfile
        ProfileDto.ProfileRequest updateReq = new ProfileDto.ProfileRequest();
        updateReq.setLatitude(28.6139);
        updateReq.setLongitude(77.2090);
        ProfileDto.ProfileResponse updatedProfRes = profileService.updateProfile(user.getId(), updateReq);
        assertEquals(28.6139, updatedProfRes.getLatitude());
        assertEquals(77.2090, updatedProfRes.getLongitude());

        User finalUser = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(28.6139, finalUser.getLatitude());
        assertEquals(77.2090, finalUser.getLongitude());
    }
}
