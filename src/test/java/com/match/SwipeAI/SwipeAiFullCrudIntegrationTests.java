package com.match.SwipeAI;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.SwipeAI.dto.*;
import com.match.SwipeAI.enums.*;
import com.match.SwipeAI.model.*;
import com.match.SwipeAI.repository.*;
import com.match.SwipeAI.security.JwtAuthFilter;
import com.match.SwipeAI.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:swipe_crud_test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "JWT_SECRET=super_secret_test_key_minimum_32_bytes_long_swipeai_2026!",
        "SERVER_PEPPER=test_pepper_key_2026",
        "app.features.digilocker.enabled=true",
        "app.features.twilio.enabled=false"
})
class SwipeAiFullCrudIntegrationTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private SafeDateSpotRepository safeDateSpotRepository;

    @Autowired
    private UserContactShieldRepository shieldRepository;

    @Autowired
    private com.match.SwipeAI.repository.UpiOrderRepository upiOrderRepository;

    @Autowired
    private com.match.SwipeAI.repository.PaymentAuditLogRepository paymentAuditLogRepository;

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.match.SwipeAI.service.integration.OtpService otpService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    private User testUser;
    private Profile testProfile;
    private String userToken;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilter(jwtAuthFilter)
                .build();

        org.mockito.Mockito.when(otpService.sendOtp(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("VERIFY_CODE_SENT");
        org.mockito.Mockito.when(otpService.verifyOtp(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);

        // Clean database state before test
        paymentAuditLogRepository.deleteAll();
        upiOrderRepository.deleteAll();
        chatMessageRepository.deleteAll();
        matchRepository.deleteAll();
        shieldRepository.deleteAll();
        profileRepository.deleteAll();
        userRepository.deleteAll();

        // Create fresh unique test user
        String testPhone = "+9198" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000));
        testUser = userRepository.save(User.builder()
                .phoneE164(testPhone)
                .gender(Gender.MALE)
                .intent(DatingIntent.SERIOUS_DATING)
                .birthDate(LocalDate.of(1998, 5, 15))
                .digilockerVerified(true)
                .whatsappVerified(true)
                .livenessScore(0.95)
                .karmaScore(100)
                .sparksBalance(5)
                .boostsBalance(2)
                .directDmsBalance(3)
                .hasActivePass(true)
                .build());

        testProfile = profileRepository.save(Profile.builder()
                .userId(testUser.getId())
                .displayName("Rohan Verma")
                .bio("Building deep tech in Bengaluru")
                .company("SwipeAI")
                .job("Software Engineer")
                .occupation("Software Engineer")
                .education("IIT Bombay")
                .city("Bengaluru")
                .neighborhood("Koramangala")
                .smokingHabit("Non-Smoker")
                .drinkingHabit("Social Drinker")
                .vacationPreference("Mountains")
                .hobbies("Trekking, Filter Coffee")
                .dietaryPref(DietaryPreference.PURE_VEG)
                .livingStatus(LivingStatus.INDEPENDENT_FLAT)
                .zodiacSign("Taurus")
                .photosJson("[\"https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500\"]")
                .build());

        userToken = jwtUtil.generateToken(testUser.getId(), testUser.getPhoneE164());
    }

    // ==========================================
    // 1. AUTHENTICATION CRUD TESTS
    // ==========================================
    @Test
    @DisplayName("Auth CRUD: OTP send, OTP verify, and WhatsApp 1-tap login")
    void testAuthCrud() throws Exception {
        String uniquePhone = "+9197" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000));

        // 1. Send OTP
        mockMvc.perform(post("/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phoneE164", uniquePhone))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").exists());

        // 2. Verify OTP (Creates user & returns JWT)
        String verifyJson = "{\"phoneE164\":\"" + uniquePhone + "\",\"otp\":\"1234\",\"gender\":\"FEMALE\",\"intent\":\"SERIOUS_DATING\",\"birthDate\":\"2000-01-01\"}";

        mockMvc.perform(post("/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.userId").exists());

        // 3. WhatsApp 1-tap instant login
        String waJson = "{\"phoneE164\":\"" + uniquePhone + "\",\"authCode\":\"mock_wa_auth_code_123\",\"gender\":\"FEMALE\",\"intent\":\"SERIOUS_DATING\",\"birthDate\":\"2000-01-01\"}";

        mockMvc.perform(post("/v1/auth/whatsapp/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(waJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.whatsappVerified").value(true));
    }

    // ==========================================
    // 2. PROFILE CRUD TESTS (Create, Read, Update, Delete)
    // ==========================================
    @Test
    @DisplayName("Profile CRUD: GET me, PUT me, POST voice prompt, POST meme swipe, GET id, and DELETE me")
    void testProfileCrud() throws Exception {
        // READ Profile
        mockMvc.perform(get("/v1/profiles/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Rohan Verma"))
                .andExpect(jsonPath("$.smokingHabit").value("Non-Smoker"))
                .andExpect(jsonPath("$.vacationPreference").value("Mountains"));

        // UPDATE Profile (Modify habits, bio, job, etc.)
        ProfileDto.ProfileRequest updateReq = new ProfileDto.ProfileRequest();
        updateReq.setDisplayName("Rohan V.");
        updateReq.setBio("Updated bio for 2026");
        updateReq.setSmokingHabit("Social Smoker");
        updateReq.setDrinkingHabit("Non-Alcoholic");
        updateReq.setVacationPreference("Beaches");
        updateReq.setHobbies("Surfing, Indie Music");
        updateReq.setJob("Lead Architect");
        updateReq.setDietaryPref(DietaryPreference.EGGETARIAN);

        mockMvc.perform(put("/v1/profiles/me")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Rohan V."))
                .andExpect(jsonPath("$.bio").value("Updated bio for 2026"))
                .andExpect(jsonPath("$.smokingHabit").value("Social Smoker"))
                .andExpect(jsonPath("$.drinkingHabit").value("Non-Alcoholic"))
                .andExpect(jsonPath("$.vacationPreference").value("Beaches"))
                .andExpect(jsonPath("$.dietaryPref").value("EGGETARIAN"));

        // CREATE / UPDATE Voice Prompt
        ProfileDto.VoicePromptUploadRequest voiceReq = new ProfileDto.VoicePromptUploadRequest();
        voiceReq.setVoicePromptUrl("https://audio.swipeai.in/prompt123.mp3");
        voiceReq.setDurationSec(15);
        voiceReq.setPromptText("Hey there, let's grab filter coffee in Indiranagar!");

        mockMvc.perform(post("/v1/profiles/voice-prompt")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voiceReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voicePromptUrl").value("https://audio.swipeai.in/prompt123.mp3"));

        // CREATE Meme Swipe
        ProfileDto.MemeSwipeRequest memeReq = new ProfileDto.MemeSwipeRequest();
        memeReq.setMemeId("meme_01");
        memeReq.setLiked(true);

        mockMvc.perform(post("/v1/profiles/meme-dna/swipe")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(memeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // READ Public Profile by ID
        mockMvc.perform(get("/v1/profiles/" + testUser.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(testUser.getId().toString()))
                .andExpect(jsonPath("$.displayName").value("Rohan V."));

        // READ Cosmic Chemistry synergy
        mockMvc.perform(get("/v1/profiles/cosmic-chemistry/" + testUser.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallSynergyScore").isNumber());

        // DELETE Profile and Account
        mockMvc.perform(delete("/v1/profiles/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Verify account is removed from database
        assertFalse(userRepository.existsById(testUser.getId()));
        assertFalse(profileRepository.existsById(testUser.getId()));
    }

    // ==========================================
    // 3. DISCOVERY & FEED CRUD TESTS
    // ==========================================
    @Test
    @DisplayName("Discovery CRUD: GET feed, POST interact (Like, Pass, Super Chai), GET circles")
    void testDiscoveryCrud() throws Exception {
        String candPhone1 = "+9196" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000));
        String candPhone2 = "+9195" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000));

        // Create candidate 1
        User candUser1 = userRepository.save(User.builder()
                .phoneE164(candPhone1)
                .gender(Gender.FEMALE)
                .intent(DatingIntent.SERIOUS_DATING)
                .birthDate(LocalDate.of(1999, 8, 20))
                .build());
        profileRepository.save(Profile.builder()
                .userId(candUser1.getId())
                .displayName("Ananya Sharma")
                .bio("Product Designer")
                .city("Bengaluru")
                .neighborhood("Indiranagar")
                .vacationPreference("Mountains")
                .smokingHabit("Non-Smoker")
                .dietaryPref(DietaryPreference.PURE_VEG)
                .photosJson("[\"https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=500\"]")
                .build());

        // Create candidate 2
        User candUser2 = userRepository.save(User.builder()
                .phoneE164(candPhone2)
                .gender(Gender.FEMALE)
                .intent(DatingIntent.SERIOUS_DATING)
                .birthDate(LocalDate.of(2000, 4, 12))
                .build());
        profileRepository.save(Profile.builder()
                .userId(candUser2.getId())
                .displayName("Tara Sen")
                .bio("Architect")
                .city("Bengaluru")
                .neighborhood("Koramangala")
                .photosJson("[\"https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500\"]")
                .build());

        // 1. GET discovery feed
        mockMvc.perform(post("/v1/discovery/feed")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates").isArray())
                .andExpect(jsonPath("$.data.remainingDailySwipes").isNumber());

        // 2. CREATE interaction (LIKE on candidate 1)
        DiscoveryDto.InteractionRequest likeReq = new DiscoveryDto.InteractionRequest();
        likeReq.setTargetId(candUser1.getId());
        likeReq.setActionType(ActionType.LIKE);
        likeReq.setContextType(ContextType.PHOTO);

        mockMvc.perform(post("/v1/discovery/interact")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(likeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // 3. CREATE interaction (SUPER CHAI on candidate 2)
        DiscoveryDto.InteractionRequest chaiReq = new DiscoveryDto.InteractionRequest();
        chaiReq.setTargetId(candUser2.getId());
        chaiReq.setActionType(ActionType.SUPER_CHAI);
        chaiReq.setContextType(ContextType.PHOTO);
        chaiReq.setCommentText("Loved your travel pictures!");

        mockMvc.perform(post("/v1/discovery/interact")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chaiReq)))
                .andExpect(status().isOk());

        // 4. READ micro-circles
        mockMvc.perform(get("/v1/discovery/circles")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ==========================================
    // 4. MATCH & CHAT CRUD TESTS
    // ==========================================
    @Test
    @DisplayName("Match & Chat CRUD: Match creation, Quiz answer, Sparks, Send message, Read messages, Clear chat, and Unmatch")
    void testMatchAndChatCrud() throws Exception {
        String userBPhone = "+9195" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000));

        // Create candidate user
        User userB = userRepository.save(User.builder()
                .phoneE164(userBPhone)
                .gender(Gender.FEMALE)
                .intent(DatingIntent.SERIOUS_DATING)
                .birthDate(LocalDate.of(2001, 3, 10))
                .build());
        profileRepository.save(Profile.builder()
                .userId(userB.getId())
                .displayName("Pooja Nair")
                .build());

        // Create match
        Match match = matchRepository.save(Match.builder()
                .userAId(testUser.getId())
                .userBId(userB.getId())
                .status(MatchStatus.ACTIVE_CHAT)
                .expiresAt(OffsetDateTime.now().plusDays(2))
                .build());

        // 1. READ Matches
        mockMvc.perform(get("/v1/matches")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // 2. READ Match Details
        mockMvc.perform(get("/v1/matches/" + match.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(match.getId().toString()))
                .andExpect(jsonPath("$.otherUserName").value("Pooja Nair"));

        // 3. UPDATE Icebreaker answer
        mockMvc.perform(post("/v1/matches/" + match.getId() + "/icebreaker/answer")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("selectedOptionIndex", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // 4. READ Wingman sparks
        mockMvc.perform(get("/v1/matches/" + match.getId() + "/wingman/sparks")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sparks").isArray());

        // 5. CREATE Chat Message
        ChatDto.SendMessageRequest msgReq = new ChatDto.SendMessageRequest();
        msgReq.setContent("Hello Pooja, great to connect!");
        msgReq.setMediaType(MessageType.TEXT);

        mockMvc.perform(post("/v1/chat/" + match.getId() + "/messages")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(msgReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hello Pooja, great to connect!"))
                .andExpect(jsonPath("$.fromMe").value(true));

        // 5b. VERIFY DATABASE STORAGE: Content must be stored as AES-256-GCM ciphertext
        String rawDbContent = jdbcTemplate.queryForObject(
                "SELECT content FROM chat_messages WHERE match_id = ?",
                String.class,
                match.getId()
        );
        assertNotNull(rawDbContent);
        assertTrue(rawDbContent.startsWith("ENC_GCM:v1:"), "Database table content must be encrypted with AES-256-GCM!");
        assertNotEquals("Hello Pooja, great to connect!", rawDbContent, "Database table must NEVER store plaintext!");

        // 6. READ Chat Messages (Authorized User A)
        mockMvc.perform(get("/v1/chat/" + match.getId() + "/messages")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].content").value("Hello Pooja, great to connect!"))
                .andExpect(jsonPath("$[0].encrypted").value(true))
                .andExpect(jsonPath("$[0].encryptionAlgo").value("AES-256-GCM"));

        // 6b. REJECT UNAUTHORIZED USER: An unassociated user cannot decrypt or read this match
        String thirdPartyPhone = "+919988776655";
        User thirdPartyUser = userRepository.save(User.builder().phoneE164(thirdPartyPhone).build());
        String thirdPartyToken = jwtUtil.generateToken(thirdPartyUser.getId(), thirdPartyPhone);

        mockMvc.perform(get("/v1/chat/" + match.getId() + "/messages")
                        .header("Authorization", "Bearer " + thirdPartyToken))
                .andExpect(status().isForbidden());

        // 6c. MARK AS READ: Recipient marks messages as read
        String userBToken = jwtUtil.generateToken(userB.getId(), userBPhone);
        mockMvc.perform(post("/v1/chat/" + match.getId() + "/read")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // 7. DELETE / Clear Chat Messages
        mockMvc.perform(delete("/v1/chat/" + match.getId() + "/messages")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Verify messages are cleared
        assertEquals(0, chatMessageRepository.countByMatchId(match.getId()));

        // 8. DELETE Match (Unmatch)
        mockMvc.perform(delete("/v1/matches/" + match.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Verify match status changed to UNMATCHED
        Match updatedMatch = matchRepository.findById(match.getId()).orElseThrow();
        assertEquals(MatchStatus.UNMATCHED, updatedMatch.getStatus());
    }

    // ==========================================
    // 5. IMAGE UPLOAD CRUD TESTS
    // ==========================================
    @Test
    @DisplayName("Image CRUD: Presigned URL, Direct Multipart upload, Mock upload, and Delete")
    void testImageCrud() throws Exception {
        // 1. GET Presigned URL
        mockMvc.perform(get("/v1/images/presign")
                        .param("filename", "my_avatar.jpg")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").exists())
                .andExpect(jsonPath("$.fileId").exists());

        // 2. POST Direct Multipart Upload
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "image content".getBytes());
        mockMvc.perform(multipart("/v1/images/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").exists())
                .andExpect(jsonPath("$.publicUrl").exists())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // 3. POST JSON Base64 Upload
        String base64Payload = java.util.Base64.getEncoder().encodeToString("test base64 content".getBytes());
        mockMvc.perform(post("/v1/images/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"test_b64.jpg\",\"contentType\":\"image/jpeg\",\"base64Data\":\"" + base64Payload + "\"}")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").exists())
                .andExpect(jsonPath("$.publicUrl").exists())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // 4. DELETE image
        mockMvc.perform(delete("/v1/images/img_test_1234")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    // ==========================================
    // 6. KYC & LIVENESS CRUD TESTS
    // ==========================================
    @Test
    @DisplayName("KYC CRUD: DigiLocker initiate, verify proof, and 3D biometric liveness")
    void testKycCrud() throws Exception {
        // 1. Initiate DigiLocker
        mockMvc.perform(post("/v1/kyc/digilocker/initiate")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authUrl").exists())
                .andExpect(jsonPath("$.stateToken").exists());

        // 2. Verify DigiLocker Proof
        KycDto.DigiLockerProofRequest proofReq = new KycDto.DigiLockerProofRequest("mock_state", "code_123", true);
        mockMvc.perform(post("/v1/kyc/digilocker/verify-proof")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proofReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.badge").value("GOLD_SHIELD"));

        // 3. Verify Biometric Liveness
        KycDto.LivenessRequest liveReq = new KycDto.LivenessRequest();
        liveReq.setHeadTurnDurationMs(3500);
        liveReq.setSelfieFrameBase64("base64_encoded_frame_data");
        liveReq.setSimulatePass(true);

        mockMvc.perform(post("/v1/kyc/liveness/verify")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(liveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liveHuman").value(true))
                .andExpect(jsonPath("$.livenessScore").isNumber());
    }

    // ==========================================
    // 7. PAYMENTS & MONETIZATION CRUD TESTS
    // ==========================================
    @Test
    @DisplayName("Payments CRUD: Catalog, Create order, Test confirm, and Webhook")
    void testPaymentCrud() throws Exception {
        // 1. READ store catalog
        mockMvc.perform(get("/v1/payments/store/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].sku").exists());

        // 2. CREATE UPI Order
        PaymentDto.CreateOrderRequest orderReq = new PaymentDto.CreateOrderRequest();
        orderReq.setSku(SkuType.SUPER_SPARK_19);
        orderReq.setVpa("rohan@okaxis");

        String orderResponse = mockMvc.perform(post("/v1/payments/upi/create-order")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.upiIntentUrl").exists())
                .andReturn().getResponse().getContentAsString();

        String orderId = objectMapper.readTree(orderResponse).get("orderId").asText();

        // 3. Test Confirm Order
        mockMvc.perform(post("/v1/payments/upi/test-confirm/" + orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // 4. Handle UPI Webhook
        mockMvc.perform(post("/v1/payments/upi/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\": \"payment.captured\", \"payload\": {\"payment\": {\"entity\": {\"order_id\": \"order_mock_123\"}}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());

        // 5. Native In-App Purchase (IAP) Verification (Google Play / Apple StoreKit)
        PaymentDto.IapVerifyRequest iapReq = PaymentDto.IapVerifyRequest.builder()
                .platform("android")
                .productId("blunderr_chai_29")
                .purchaseToken("mock_purchase_token_gplay_123")
                .orderId("iap_gplay_" + System.currentTimeMillis())
                .sku(SkuType.CUTTING_CHAI_21)
                .build();

        mockMvc.perform(post("/v1/payments/iap/verify")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(iapReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sku").value("CUTTING_CHAI_21"));

        // 6. Cashfree Create Order & Test Confirm
        PaymentDto.CashfreeCreateOrderRequest cfReq = PaymentDto.CashfreeCreateOrderRequest.builder()
                .sku(SkuType.WEEKEND_PASS_99)
                .customerPhone("+919876543210")
                .build();

        String cfResponse = mockMvc.perform(post("/v1/payments/cashfree/create-order")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cfReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.paymentSessionId").exists())
                .andReturn().getResponse().getContentAsString();

        String cfOrderId = objectMapper.readTree(cfResponse).get("orderId").asText();

        mockMvc.perform(post("/v1/payments/cashfree/test-confirm/" + cfOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // 7. Verify Database Zero-Plaintext Encryption at rest
        UpiOrder confirmedUpiOrder = upiOrderRepository.findByOrderId(orderId).orElseThrow();
        assertNotNull(confirmedUpiOrder.getRawPayloadEncrypted(), "Raw payload must be encrypted at rest");
        assertTrue(confirmedUpiOrder.getRawPayloadEncrypted().startsWith("ENC_PAY_GCM:v1:"),
                "Payload must be encrypted with AES-256-GCM authenticated prefix");

        // 8. Trace Audit Timeline: GET /v1/payments/audit/orders/{orderId}
        mockMvc.perform(get("/v1/payments/audit/orders/" + orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andExpect(jsonPath("$.paymentProvider").value("RAZORPAY_UPI"))
                .andExpect(jsonPath("$.events", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.events[0].event").value("ORDER_INITIATED"))
                .andExpect(jsonPath("$.decryptedRawPayload").exists());

        // 9. User Historical Audit: GET /v1/payments/audit/user/{userId}
        mockMvc.perform(get("/v1/payments/audit/user/" + testUser.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));

        // 10. Manual Support Review: POST /v1/payments/audit/orders/{orderId}/review
        PaymentDto.ReviewOrderRequest reviewReq = new PaymentDto.ReviewOrderRequest();
        reviewReq.setAdminNotes("Reconciled manually with bank UTR #982348572");
        reviewReq.setStatus(com.match.SwipeAI.enums.OrderStatus.CAPTURED);
        reviewReq.setGrantPerks(false);
        reviewReq.setAdminIdOrName("SupervisorSunil");

        mockMvc.perform(post("/v1/payments/audit/orders/" + orderId + "/review")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.adminNotes").value("Reconciled manually with bank UTR #982348572"))
                .andExpect(jsonPath("$.reviewedBy").value("SupervisorSunil"))
                .andExpect(jsonPath("$.events[-1].event").value("MANUAL_SUPPORT_REVIEW"));
    }

    // ==========================================
    // 8. SAFE DATE CRUD TESTS
    // ==========================================
    @Test
    @DisplayName("Safe Date CRUD: GET spots and POST start SOS")
    void testSafeDateCrud() throws Exception {
        // Ensure at least one cafe spot in database with non-null coordinates
        SafeDateSpot spot = safeDateSpotRepository.save(SafeDateSpot.builder()
                .name("Blue Tokai Coffee Roasters")
                .brand("Blue Tokai")
                .address("100 Feet Rd, Indiranagar")
                .city("Bengaluru")
                .neighborhood("Indiranagar")
                .latitude(12.9716)
                .longitude(77.5946)
                .couponCode("SWIPEAI15")
                .discountPercent(15)
                .sosEnabled(true)
                .build());

        // 1. READ Safe Spots
        mockMvc.perform(get("/v1/safe-date/spots")
                        .param("city", "Bengaluru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].name").exists());

        // 2. CREATE SOS tracking session
        SafeDateDto.SosStartRequest sosReq = new SafeDateDto.SosStartRequest();
        sosReq.setSafeSpotId(spot.getId());
        sosReq.setEmergencyContacts(List.of("+919876543211", "+919876543212"));

        mockMvc.perform(post("/v1/safe-date/sos/start")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sosReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.trackingUrl").exists());
    }

    // ==========================================
    // 9. SHADOW SHIELD CRUD TESTS
    // ==========================================
    @Test
    @DisplayName("Shadow Shield CRUD: Sync contacts, Corporate domain, Read status, and Clear contacts")
    void testShadowShieldCrud() throws Exception {
        // 1. CREATE contact hashes
        ShieldDto.SyncContactsRequest syncReq = new ShieldDto.SyncContactsRequest();
        syncReq.setContactHashes(List.of(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb"
        ));

        mockMvc.perform(post("/v1/privacy/shadow-shield/sync-contacts")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shieldedContactsCount").isNumber())
                .andExpect(jsonPath("$.shieldActive").value(true));

        // 2. CREATE corporate domain blocker
        ShieldDto.DomainShieldRequest domainReq = new ShieldDto.DomainShieldRequest();
        domainReq.setCorporateDomain("swiggy.in");

        mockMvc.perform(post("/v1/privacy/shadow-shield/domain")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(domainReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corporateDomain").value("swiggy.in"));

        // 3. READ shield status
        mockMvc.perform(get("/v1/privacy/shadow-shield/status")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shieldActive").value(true));

        // 4. DELETE / Clear contacts
        mockMvc.perform(delete("/v1/privacy/shadow-shield/contacts")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    // ==========================================
    // 10. VIRTUAL CHAI CALLING CRUD TESTS
    // ==========================================
    @Test
    @DisplayName("Virtual Chai Calling: Create masked WebRTC session")
    void testCallingCrud() throws Exception {
        String callJson = "{\"matchId\":\"" + UUID.randomUUID() + "\",\"isVideo\":true}";

        mockMvc.perform(post("/v1/calling/virtual-chai/session")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomName").exists())
                .andExpect(jsonPath("$.participantToken").exists())
                .andExpect(jsonPath("$.callerMaskedName").exists());
    }
}
