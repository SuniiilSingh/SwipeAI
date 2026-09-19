package com.match.SwipeAI;

import com.match.SwipeAI.dto.DiscoveryDto;
import com.match.SwipeAI.dto.KycDto;
import com.match.SwipeAI.dto.MatchDto;
import com.match.SwipeAI.dto.PaymentDto;
import com.match.SwipeAI.dto.ProfileDto;
import com.match.SwipeAI.dto.ShieldDto;
import com.match.SwipeAI.enums.*;
import com.match.SwipeAI.model.*;
import com.match.SwipeAI.service.DiscoveryService;
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
import java.util.Optional;
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

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private com.match.SwipeAI.service.DesireProfileService desireProfileService;

    @Autowired
    private com.match.SwipeAI.repository.DesireProfileRepository desireProfileRepository;

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

        // 3. Test updating location, company, and institute via updateProfile
        ProfileDto.ProfileRequest updateReq = new ProfileDto.ProfileRequest();
        updateReq.setLatitude(28.6139);
        updateReq.setLongitude(77.2090);
        updateReq.setCompany("Google");
        updateReq.setInstitute("IIT Bombay");
        ProfileDto.ProfileResponse updatedProfRes = profileService.updateProfile(user.getId(), updateReq);
        assertEquals(28.6139, updatedProfRes.getLatitude());
        assertEquals(77.2090, updatedProfRes.getLongitude());
        assertEquals("Google", updatedProfRes.getCompany());
        assertEquals("IIT Bombay", updatedProfRes.getInstitute());

        User finalUser = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(28.6139, finalUser.getLatitude());
        assertEquals(77.2090, finalUser.getLongitude());
    }

    @Test
    void testDistanceCalculation_AccurateAndCityResolution() {
        // 1. Direct GPS coordinates: Koramangala to Indiranagar (~5.1 km)
        double koramangalaLat = 12.9352, koramangalaLon = 77.6245;
        double indiranagarLat = 12.9784, indiranagarLon = 77.6408;
        double distKm = matchEngine.calculateDistanceKm(koramangalaLat, koramangalaLon, indiranagarLat, indiranagarLon);
        assertTrue(distKm >= 4.5 && distKm <= 5.5, "Expected Koramangala to Indiranagar distance ~5.1 km, got: " + distKm);

        // 2. City resolution when coordinates are missing: Mumbai to Bengaluru (~845 km)
        Profile mumbaiProf = Profile.builder().city("Mumbai").build();
        Profile blrProf = Profile.builder().city("Bengaluru").build();
        double interCityDist = matchEngine.calculateDistanceWithContext(null, null, mumbaiProf, null, null, blrProf);
        assertTrue(interCityDist >= 800.0 && interCityDist <= 900.0, "Expected Mumbai to Bengaluru ~845 km, got: " + interCityDist);

        // 3. Same city neighborhood resolution: Indiranagar to Koramangala via profiles
        Profile hoodA = Profile.builder().city("Bengaluru").neighborhood("Indiranagar").build();
        Profile hoodB = Profile.builder().city("Bengaluru").neighborhood("Koramangala").build();
        double resolvedHoodDist = matchEngine.calculateDistanceWithContext(null, null, hoodA, null, null, hoodB);
        assertTrue(resolvedHoodDist >= 4.5 && resolvedHoodDist <= 5.5, "Expected resolved neighborhood distance ~5.1 km, got: " + resolvedHoodDist);
    }

    @Test
    void testDiscoveryFeed_RadiusFiltering() {
        // Setup viewer in Bengaluru Central
        User viewer = userRepository.save(User.builder()
                .phoneE164("+9197" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000)))
                .gender(Gender.MALE)
                .intent(DatingIntent.SERIOUS_DATING)
                .birthDate(LocalDate.of(1996, 5, 10))
                .latitude(12.9752) // Church Street / Central
                .longitude(77.6053)
                .build());

        Profile viewerProfile = profileRepository.save(Profile.builder()
                .userId(viewer.getId())
                .displayName("Viewer User")
                .genderDisplay("Man")
                .sexualOrientation("Heterosexual")
                .dietaryPref(DietaryPreference.PURE_VEG)
                .livingStatus(LivingStatus.INDEPENDENT_FLAT)
                .photosJson("[\"https://images.unsplash.com/photo-1?w=500\"]")
                .city("Bengaluru")
                .neighborhood("Central")
                .maxDistanceKm(10) // 1. Profile radius filled as 10 km
                .build());

        // Setup candidate 1: Close by in Central (~1 km)
        User closeCandidate = userRepository.save(User.builder()
                .phoneE164("+9197" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000)))
                .gender(Gender.FEMALE)
                .intent(DatingIntent.SERIOUS_DATING)
                .birthDate(LocalDate.of(1998, 3, 14))
                .latitude(12.9716)
                .longitude(77.5946)
                .build());
        profileRepository.save(Profile.builder()
                .userId(closeCandidate.getId())
                .displayName("Close Candidate")
                .genderDisplay("Woman")
                .sexualOrientation("Heterosexual")
                .city("Bengaluru")
                .photosJson("[\"https://images.unsplash.com/photo-2?w=500\"]")
                .build());

        // Setup candidate 2: Far away in Mumbai (~845 km)
        User farCandidate = userRepository.save(User.builder()
                .phoneE164("+9197" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000)))
                .gender(Gender.FEMALE)
                .intent(DatingIntent.SERIOUS_DATING)
                .birthDate(LocalDate.of(1997, 8, 22))
                .latitude(19.0760)
                .longitude(72.8777)
                .build());
        profileRepository.save(Profile.builder()
                .userId(farCandidate.getId())
                .displayName("Far Candidate")
                .genderDisplay("Woman")
                .sexualOrientation("Heterosexual")
                .city("Mumbai")
                .photosJson("[\"https://images.unsplash.com/photo-3?w=500\"]")
                .build());

        // 1. Fetch feed when profile radius is 10 km
        DiscoveryDto.DiscoveryFeedResponse feed10km = discoveryService.getDiscoveryFeed(viewer.getId(), new DiscoveryDto.DiscoveryFeedRequest());

        List<UUID> returnedIds10km = feed10km.getData().getCandidates().stream()
                .map(DiscoveryDto.CandidateCardDto::getUserId).toList();

        // Close candidate must be present, far candidate MUST be excluded
        assertTrue(returnedIds10km.contains(closeCandidate.getId()));
        assertFalse(returnedIds10km.contains(farCandidate.getId()), "Far candidate in Mumbai must be excluded when profile radius is 10 km");

        // 2. When profile radius is null, verify default 50 km is applied
        viewerProfile.setMaxDistanceKm(null);
        profileRepository.save(viewerProfile);

        DiscoveryDto.DiscoveryFeedResponse feedNull = discoveryService.getDiscoveryFeed(viewer.getId(), new DiscoveryDto.DiscoveryFeedRequest());
        List<UUID> returnedIdsNull = feedNull.getData().getCandidates().stream()
                .map(DiscoveryDto.CandidateCardDto::getUserId).toList();

        assertTrue(returnedIdsNull.contains(closeCandidate.getId()), "Close candidate within 50 km default must be included");
        assertFalse(returnedIdsNull.contains(farCandidate.getId()), "Candidate in Mumbai (845 km) must be excluded under 50 km default");

        // 3. When profile radius is 1000 km
        viewerProfile.setMaxDistanceKm(1000);
        profileRepository.save(viewerProfile);

        DiscoveryDto.DiscoveryFeedResponse feed1000km = discoveryService.getDiscoveryFeed(viewer.getId(), new DiscoveryDto.DiscoveryFeedRequest());
        List<UUID> returnedIds1000km = feed1000km.getData().getCandidates().stream()
                .map(DiscoveryDto.CandidateCardDto::getUserId).toList();

        assertTrue(returnedIds1000km.contains(closeCandidate.getId()));
        assertTrue(returnedIds1000km.contains(farCandidate.getId()), "Far candidate in Mumbai should be included when profile radius is 1000 km");
    }

    @Autowired
    private com.match.SwipeAI.service.integration.LiveKitCallingService liveKitCallingService;

    @Test
    void testVirtualChaiCallingSessionGeneration() {
        UUID matchId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        com.match.SwipeAI.dto.CallingDto.VirtualChaiSessionResponse response =
                liveKitCallingService.createCallingSession(matchId, callerId, "Aarav Sharma", "Ananya Verma", true);

        assertNotNull(response);
        assertNotNull(response.getRoomName());
        assertTrue(response.getRoomName().startsWith("chai_room_"));
        assertNotNull(response.getParticipantToken());
        assertTrue(response.isPhoneMasked());
        assertTrue(response.isVideo());
        assertEquals("Aarav Sharma", response.getCallerMaskedName());
        assertEquals("Ananya Verma", response.getRecipientMaskedName());
    }

    @Test
    void testIcebreakerQuizDeserializationWithIsCompleted() {
        String jsonWithIsCompleted = "{\"quizId\":\"quiz_sunday_vibe\",\"title\":\"10s Rapid-Fire Quiz\",\"question\":\"Your Ultimate Sunday Vibe:\",\"options\":[\"Filter Coffee & Dosa crawl in Indiranagar\",\"Sleep until 2 PM & binge true-crime podcasts\"],\"userAAnswer\":0,\"userBAnswer\":1,\"isCompleted\":true,\"isMutualAgreement\":false}";

        MatchDto.IcebreakerQuizDto parsed = icebreakerEngine.parseQuizData(jsonWithIsCompleted);
        assertNotNull(parsed);
        assertEquals("quiz_sunday_vibe", parsed.getQuizId());
        assertTrue(parsed.isCompleted(), "isCompleted should be parsed as true");
        assertFalse(parsed.isMutualAgreement(), "isMutualAgreement should be parsed as false");
        assertEquals(0, parsed.getUserAAnswer());
        assertEquals(1, parsed.getUserBAnswer());

        // Also test with 'completed' standard JavaBean format
        String jsonWithCompleted = "{\"quizId\":\"quiz_sunday_vibe\",\"title\":\"10s Rapid-Fire Quiz\",\"question\":\"Your Ultimate Sunday Vibe:\",\"options\":[\"Filter Coffee\"],\"completed\":true,\"mutualAgreement\":true}";
        MatchDto.IcebreakerQuizDto parsed2 = icebreakerEngine.parseQuizData(jsonWithCompleted);
        assertNotNull(parsed2);
        assertTrue(parsed2.isCompleted(), "completed should be parsed as true");
        assertTrue(parsed2.isMutualAgreement(), "mutualAgreement should be parsed as true");
    }

    @Test
    void testDesireProfile_DefaultTemplateIsNotConfigured() {
        UUID newUserId = UUID.randomUUID();
        com.match.SwipeAI.dto.DesireDto.DesireProfileResponse defaultDesire = desireProfileService.getDesireProfile(newUserId);
        assertNotNull(defaultDesire);
        assertFalse(Boolean.TRUE.equals(defaultDesire.getIsConfigured()), "Fresh user must have isConfigured = false");

        // Now save desire profile
        com.match.SwipeAI.dto.DesireDto.DesireProfileRequest req = com.match.SwipeAI.dto.DesireDto.DesireProfileRequest.builder()
                .minAge(23)
                .maxAge(28)
                .dietaryHarmony("VEG_SPECTRUM")
                .weekendVibe("COFFEE_AND_BOOKS")
                .build();
        com.match.SwipeAI.dto.DesireDto.DesireProfileResponse saved = desireProfileService.saveDesireProfile(newUserId, req);
        assertNotNull(saved);
        assertTrue(Boolean.TRUE.equals(saved.getIsConfigured()), "Saved desire profile must have isConfigured = true");
    }

    @Test
    void testMatchEngine_WithoutDesireProfile_UsesEarlierApproach() {
        User viewer = userRepository.save(User.builder()
                .phoneE164("+9197" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000)))
                .gender(Gender.MALE)
                .intent(DatingIntent.SERIOUS_DATING)
                .birthDate(LocalDate.of(1998, 5, 20)) // 26 yrs
                .latitude(12.9716)
                .longitude(77.5946)
                .build());

        profileRepository.save(Profile.builder()
                .userId(viewer.getId())
                .displayName("Rohit Kumar")
                .genderDisplay("Man")
                .sexualOrientation("Heterosexual")
                .genderPreferenceDisplay("Women")
                .city("Bengaluru")
                .maxDistanceKm(50)
                .dietaryPref(DietaryPreference.PURE_VEG)
                .photosJson("[\"https://images.unsplash.com/photo-1?w=500\"]")
                .build());

        // Candidate 1: Female, 35 yrs old (outside typical 20-30 range, but within 50km)
        User cand = userRepository.save(User.builder()
                .phoneE164("+9197" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000)))
                .gender(Gender.FEMALE)
                .intent(DatingIntent.SERIOUS_DATING)
                .birthDate(LocalDate.of(1989, 3, 10)) // 35 yrs
                .latitude(12.9750)
                .longitude(77.6000)
                .build());

        profileRepository.save(Profile.builder()
                .userId(cand.getId())
                .displayName("Pooja Sharma")
                .genderDisplay("Woman")
                .sexualOrientation("Heterosexual")
                .genderPreferenceDisplay("Men")
                .city("Bengaluru")
                .dietaryPref(DietaryPreference.NON_VEG)
                .photosJson("[\"https://images.unsplash.com/photo-2?w=500\"]")
                .build());

        // Because viewer has NOT configured desire profile, earlier approach applies:
        // Candidate is included, and desireMatchPercent on card is null!
        DiscoveryDto.DiscoveryFeedRequest feedReq = new DiscoveryDto.DiscoveryFeedRequest();
        feedReq.setLimit(100);
        DiscoveryDto.DiscoveryFeedResponse feed = discoveryService.getDiscoveryFeed(viewer.getId(), feedReq);
        assertNotNull(feed);
        assertNotNull(feed.getData());

        Optional<DiscoveryDto.CandidateCardDto> candCard = feed.getData().getCandidates().stream()
                .filter(c -> c.getUserId().equals(cand.getId()))
                .findFirst();

        assertTrue(candCard.isPresent(), "Without desire profile, earlier approach should include candidate within radius");
        assertNull(candCard.get().getDesireMatchPercent(), "Candidate card should not display desireMatchPercent when desire profile is not configured");
    }

    @Test
    void testMatchEngine_WithDesireProfile_FiltersAndBlendsScore() {
        User viewer = userRepository.save(User.builder()
                .phoneE164("+9197" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000)))
                .gender(Gender.MALE)
                .intent(DatingIntent.SERIOUS_DATING)
                .birthDate(LocalDate.of(1998, 5, 20))
                .latitude(12.9716)
                .longitude(77.5946)
                .build());

        profileRepository.save(Profile.builder()
                .userId(viewer.getId())
                .displayName("Vikram Malhotra")
                .genderDisplay("Man")
                .sexualOrientation("Heterosexual")
                .genderPreferenceDisplay("Women")
                .city("Bengaluru")
                .dietaryPref(DietaryPreference.PURE_VEG)
                .photosJson("[\"https://images.unsplash.com/photo-1?w=500\"]")
                .build());

        // Viewer configures desire profile: Strict Jain only, Age 22-26, strict (ageFlexible = false)
        com.match.SwipeAI.dto.DesireDto.DesireProfileRequest desireReq = com.match.SwipeAI.dto.DesireDto.DesireProfileRequest.builder()
                .minAge(22)
                .maxAge(26)
                .ageFlexible(false)
                .dietaryHarmony("STRICT_JAIN_ONLY")
                .maxDistanceKm(30)
                .weekendVibe("COFFEE_AND_BOOKS")
                .greenFlags(List.of("Reads books"))
                .build();
        desireProfileService.saveDesireProfile(viewer.getId(), desireReq);

        // Candidate 1: 24 yrs old, Strict Jain, close -> SHOULD BE INCLUDED with desireMatchPercent
        User cand1 = userRepository.save(User.builder()
                .phoneE164("+9197" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000)))
                .gender(Gender.FEMALE)
                .intent(DatingIntent.SERIOUS_DATING)
                .birthDate(LocalDate.of(2000, 1, 15)) // 24 yrs
                .latitude(12.9730)
                .longitude(77.5980)
                .build());

        profileRepository.save(Profile.builder()
                .userId(cand1.getId())
                .displayName("Aanya Jain")
                .genderDisplay("Woman")
                .sexualOrientation("Heterosexual")
                .genderPreferenceDisplay("Men")
                .city("Bengaluru")
                .dietaryPref(DietaryPreference.STRICT_JAIN)
                .bio("Lover of literature and filter coffee. Reads books every morning.")
                .photosJson("[\"https://images.unsplash.com/photo-3?w=500\"]")
                .build());

        // Candidate 2: 32 yrs old (outside 22-26 strict range) -> MUST BE EXCLUDED by desire age dealbreaker
        User cand2 = userRepository.save(User.builder()
                .phoneE164("+9197" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000)))
                .gender(Gender.FEMALE)
                .intent(DatingIntent.SERIOUS_DATING)
                .birthDate(LocalDate.of(1992, 1, 15)) // 32 yrs
                .latitude(12.9730)
                .longitude(77.5980)
                .build());

        profileRepository.save(Profile.builder()
                .userId(cand2.getId())
                .displayName("Simran Kaur")
                .genderDisplay("Woman")
                .sexualOrientation("Heterosexual")
                .genderPreferenceDisplay("Men")
                .city("Bengaluru")
                .dietaryPref(DietaryPreference.STRICT_JAIN)
                .photosJson("[\"https://images.unsplash.com/photo-4?w=500\"]")
                .build());

        DiscoveryDto.DiscoveryFeedResponse feed = discoveryService.getDiscoveryFeed(viewer.getId(), new DiscoveryDto.DiscoveryFeedRequest());
        assertNotNull(feed);
        assertNotNull(feed.getData());

        List<DiscoveryDto.CandidateCardDto> cards = feed.getData().getCandidates();

        boolean cand1Found = cards.stream().anyMatch(c -> c.getUserId().equals(cand1.getId()));
        boolean cand2Found = cards.stream().anyMatch(c -> c.getUserId().equals(cand2.getId()));

        assertTrue(cand1Found, "Matching candidate within desire criteria must be included");
        assertFalse(cand2Found, "Candidate outside non-flexible desire age range must be excluded");

        DiscoveryDto.CandidateCardDto card1 = cards.stream().filter(c -> c.getUserId().equals(cand1.getId())).findFirst().orElseThrow();
        assertNotNull(card1.getDesireMatchPercent(), "Desire match percent must be populated when desire profile is configured");
        assertTrue(card1.getDesireMatchPercent() >= 70, "High desire synergy should yield >= 70% score");
        assertNotNull(card1.getDesireMatchHighlights());
        assertFalse(card1.getDesireMatchHighlights().isEmpty(), "Desire highlights should provide reasons");
    }

    @Test
    void testDesireProfile_PreferredProfessions_SavesAndMatchesCareer() {
        UUID userId = UUID.randomUUID();
        com.match.SwipeAI.dto.DesireDto.DesireProfileRequest req = com.match.SwipeAI.dto.DesireDto.DesireProfileRequest.builder()
                .minAge(22)
                .maxAge(30)
                .preferredProfessions(List.of("Software Engineer", "Doctor / Healthcare"))
                .build();

        com.match.SwipeAI.dto.DesireDto.DesireProfileResponse saved = desireProfileService.saveDesireProfile(userId, req);
        assertNotNull(saved);
        assertNotNull(saved.getPreferredProfessions());
        assertEquals(2, saved.getPreferredProfessions().size());
        assertTrue(saved.getPreferredProfessions().contains("Software Engineer"));

        // Test matching calculation with candidate profile having matching profession
        Profile candProfile = Profile.builder()
                .occupation("Senior Software Engineer")
                .job("Software Engineer")
                .build();

        DesireProfile entity = desireProfileRepository.findById(userId).orElseThrow();
        com.match.SwipeAI.service.DesireProfileService.DesireMatchResult matchResult = desireProfileService.calculateDesireMatch(entity, candProfile, 25, 5.0);
        assertNotNull(matchResult);
        assertTrue(matchResult.highlights().stream().anyMatch(h -> h.contains("Software Engineer") || h.contains("Career alignment")),
                "Highlights should mention career alignment for Software Engineer");
    }
}
