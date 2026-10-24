package com.match.SwipeAI.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.SwipeAI.dto.PaymentDto;
import com.match.SwipeAI.enums.OrderStatus;
import com.match.SwipeAI.enums.SkuType;
import com.match.SwipeAI.enums.TicketStatus;
import com.match.SwipeAI.model.*;
import com.match.SwipeAI.repository.*;
import com.match.SwipeAI.service.integration.PaymentAuditService;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Isolated BlunderR Admin Command Center Controller (/v1/admin/**).
 * Uses database-backed AdminAccount credentials (BCrypt or plain SQL insert),
 * HMAC-SHA256 signed X-Admin-Token authentication, strict RBAC roles
 * (SUPER_ADMIN vs SUPPORT_AGENT), IP brute-force rate limiting, and audit logging.
 */
@Slf4j
@CrossOrigin(exposedHeaders = {"X-Admin-Token", "X-Total-Count"})
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAccountRepository adminAccountRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final UserAstrologyRepository astrologyRepository;
    private final MatchRepository matchRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UpiOrderRepository upiOrderRepository;
    private final PaymentExecutionLogRepository paymentExecutionLogRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final MicroCommunityRepository microCommunityRepository;
    private final SafeDateSpotRepository safeDateSpotRepository;
    private final UserContactShieldRepository contactShieldRepository;
    private final UserNotificationRepository notificationRepository;
    private final UserReportRepository userReportRepository;
    private final UserBlockRepository userBlockRepository;
    private final MarketingEmailCampaignRepository marketingEmailCampaignRepository;
    private final PaymentAuditService paymentAuditService;
    private final com.match.SwipeAI.service.integration.UpiPaymentService upiPaymentService;
    private final com.match.SwipeAI.repository.AccountDeletionLogRepository accountDeletionLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    @Value("${jwt.secret:blunderr-super-secret-admin-hmac-signing-key-2026}")
    private String jwtSecret;

    @Value("${admin.super.password:Blunderr@Admin2026}")
    private String superAdminPassword;

    @Value("${admin.support.password:Blunderr@Support2026}")
    private String supportAdminPassword;

    @Value("${admin.pin:2026}")
    private String admin2faPin;

    @Value("${smtp.host:}")
    private String smtpHost;

    @Value("${smtp.port:587}")
    private int smtpPort;

    @Value("${smtp.username:}")
    private String smtpUsername;

    @Value("${smtp.password:}")
    private String smtpPassword;

    @Value("${smtp.from:offers@blunderr.in}")
    private String smtpFrom;

    // Brute-force protection: IP -> list of failed login timestamps (epoch ms)
    private final Map<String, List<Long>> failedLoginTracker = new ConcurrentHashMap<>();

    // Append-only Admin Audit Log
    private final List<AdminAuditEntry> auditLogs = new CopyOnWriteArrayList<>();

    private static final long ADMIN_TOKEN_TTL_MS = 2 * 60 * 60 * 1000L; // 2 hours

    @PostConstruct
    public void seedDefaultAdminAccountsIfEmpty() {
        try {
            if (adminAccountRepository.findByEmailIgnoreCase("admin@blunderr.in").isEmpty()) {
                adminAccountRepository.save(AdminAccount.builder()
                        .email("admin@blunderr.in")
                        .displayName("Sunil (Super Admin)")
                        .role("SUPER_ADMIN")
                        .passwordHash(passwordEncoder.encode(superAdminPassword))
                        .pinHash(passwordEncoder.encode(admin2faPin))
                        .isActive(true)
                        .failedAttempts(0)
                        .build());
                log.info("Seeded default SUPER_ADMIN account: admin@blunderr.in");
            }
            if (adminAccountRepository.findByEmailIgnoreCase("support@blunderr.in").isEmpty()) {
                adminAccountRepository.save(AdminAccount.builder()
                        .email("support@blunderr.in")
                        .displayName("Customer Support Desk")
                        .role("SUPPORT_AGENT")
                        .passwordHash(passwordEncoder.encode(supportAdminPassword))
                        .pinHash(passwordEncoder.encode(admin2faPin))
                        .isActive(true)
                        .failedAttempts(0)
                        .build());
                log.info("Seeded default SUPPORT_AGENT account: support@blunderr.in");
            }
        } catch (Exception e) {
            log.warn("Could not seed initial admin accounts: {}", e.getMessage());
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminAuditEntry {
        private String id;
        private String adminEmail;
        private String adminRole;
        private String action;
        private String targetId;
        private String details;
        private String ipAddress;
        private String timestamp;
    }

    @Data
    public static class AdminLoginRequest {
        private String email;
        private String password;
        private String pin;
    }

    @Data
    public static class VerificationDecisionRequest {
        private String decision; // APPROVE or REJECT
        private String reason;
    }

    @Data
    public static class UserActionRequest {
        private String action; // VERIFY_SELFIE, REJECT_SELFIE, TOGGLE_DIGILOCKER, TOGGLE_SHADOWBAN, GRANT_CREDITS, GRANT_MEMBERSHIP, UPDATE_KARMA, REMOVE_PHOTO, UPDATE_MARKETING_EMAIL
        private Integer sparksDelta;
        private Integer boostsDelta;
        private Integer directDmsDelta;
        private Boolean grantVipPass;
        private Integer vipDays;
        private String membershipSku; // SkuType or "CUSTOM"
        private String customPlanName;
        private Integer karmaScore;
        private Integer karmaDelta;
        private Integer photoSlot; // 1..6
        private String email;
        private Boolean marketingOptIn;
        private String reason;
    }

    @Data
    public static class TicketStatusUpdateRequest {
        private String status; // PENDING, IN_PROGRESS, RESOLVED, CLOSED
        private String resolutionNotes;
    }

    @Data
    public static class CreateCircleRequest {
        private String city;
        private String name;
        private String tagline;
        private String vibeCategory;
        private String badgeIcon;
        private Integer activeMembersCount;
        private Boolean isPopular;
    }

    @Data
    public static class CreateSafeSpotRequest {
        private String name;
        private String brand;
        private String address;
        private String city;
        private String neighborhood;
        private Double latitude;
        private Double longitude;
        private Integer discountPercent;
        private String couponCode;
    }

    @Data
    public static class BroadcastRequest {
        private String audience; // ALL_USERS, PENDING_SELFIE, UNVERIFIED_SELFIE, CITY
        private String city;
        private String title;
        private String body;
    }

    @Data
    public static class MarketingEmailRequest {
        private String audience; // OPTED_IN_ALL, OPTED_IN_CITY, OPTED_IN_NON_VIP
        private String city;
        private String subject;
        private String offerBadge; // e.g. "FLAT 50% OFF VIP PASS"
        private String couponCode; // e.g. "BLUNDERR50"
        private String ctaText;    // e.g. "Claim Offer in BlunderR"
        private String ctaUrl;     // e.g. "https://blunderr.in/app/"
        private String body;
    }

    @Data
    public static class SmtpConfigRequest {
        private String host;
        private Integer port;
        private String username;
        private String password;
        private String fromEmail;
    }

    @Data
    public static class ReportActionRequest {
        private String action; // PROFILE_SUSPENDED, WARNING_ISSUED, CONTENT_REMOVED, DISMISSED
        private String adminMessage;
        private UUID reportId; // optional
        private Boolean suspendAccount;
        private Boolean notifyReporter;
    }

    // =========================================================================
    // 1. ADMIN AUTHENTICATION & SECURITY (DB-BACKED)
    // =========================================================================

    private boolean matchesSecret(String rawInput, String storedHashOrPlain) {
        if (rawInput == null || storedHashOrPlain == null) return false;
        if (storedHashOrPlain.startsWith("$2a$") || storedHashOrPlain.startsWith("$2b$") || storedHashOrPlain.startsWith("$2y$")) {
            return passwordEncoder.matches(rawInput, storedHashOrPlain);
        }
        return storedHashOrPlain.equals(rawInput);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody AdminLoginRequest req,
            HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);
        checkRateLimit(ip);

        String email = req.getEmail() != null ? req.getEmail().trim().toLowerCase() : "";
        String password = req.getPassword() != null ? req.getPassword().trim() : "";
        String pin = req.getPin() != null ? req.getPin().trim() : "";

        AdminAccount account = adminAccountRepository.findByEmailIgnoreCase(email).orElse(null);

        if (account == null || !Boolean.TRUE.equals(account.getIsActive())) {
            recordFailedLogin(ip);
            recordAudit(email.isEmpty() ? "unknown" : email, "NONE", "ADMIN_LOGIN_FAILED", ip, "Account not found or inactive", ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin email or account is disabled");
        }

        if (account.getLockedUntil() != null && account.getLockedUntil().isAfter(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "Account temporarily locked due to failed attempts. Try again later.");
        }

        boolean passOk = matchesSecret(password, account.getPasswordHash());
        boolean pinOk = matchesSecret(pin, account.getPinHash());

        if (!passOk || !pinOk) {
            int fails = (account.getFailedAttempts() != null ? account.getFailedAttempts() : 0) + 1;
            account.setFailedAttempts(fails);
            if (fails >= 5) {
                account.setLockedUntil(OffsetDateTime.now().plusMinutes(15));
            }
            adminAccountRepository.save(account);
            recordFailedLogin(ip);
            recordAudit(email, account.getRole(), "ADMIN_LOGIN_FAILED", ip, !pinOk ? "Invalid 2FA PIN" : "Invalid password", ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, !pinOk ? "Invalid 2FA Security PIN" : "Invalid password");
        }

        account.setFailedAttempts(0);
        account.setLockedUntil(null);
        account.setLastLoginAt(OffsetDateTime.now());
        account.setLastLoginIp(ip);
        adminAccountRepository.save(account);

        String role = account.getRole().toUpperCase();
        long expiresAt = System.currentTimeMillis() + ADMIN_TOKEN_TTL_MS;
        String token = generateAdminToken(account.getEmail(), role, expiresAt);

        recordAudit(account.getEmail(), role, "ADMIN_LOGIN_SUCCESS", account.getEmail(), "Signed in to BlunderR Command Center", ip);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "email", account.getEmail(),
                "role", role,
                "displayName", account.getDisplayName(),
                "expiresAt", expiresAt
        ));
    }

    @GetMapping("/auth/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        AdminSession session = requireAdmin(request, "SUPER_ADMIN", "SUPPORT_AGENT");
        return ResponseEntity.ok(Map.of(
                "email", session.email,
                "role", session.role,
                "expiresAt", session.expiresAt
        ));
    }

    // =========================================================================
    // 2. MODULE 1: EXECUTIVE PULSE OVERVIEW
    // =========================================================================

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview(HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN");

        List<User> allUsers = userRepository.findAll();
        List<Profile> allProfiles = profileRepository.findAll();
        Map<UUID, Profile> profileMap = allProfiles.stream()
                .collect(Collectors.toMap(Profile::getUserId, p -> p, (a, b) -> a));

        long totalUsers = allUsers.size();
        OffsetDateTime now = OffsetDateTime.now();
        long signups24h = allUsers.stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(now.minusHours(24)))
                .count();
        long signups7d = allUsers.stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(now.minusDays(7)))
                .count();

        long verifiedSelfie = 0;
        long pendingSelfie = 0;
        long rejectedSelfie = 0;
        long unverifiedSelfie = 0;
        long twoOrMorePhotos = 0;

        Map<String, Long> cityCounts = new HashMap<>();

        for (User u : allUsers) {
            Profile p = profileMap.get(u.getId());
            String status = resolveVerificationStatus(u, p);
            switch (status) {
                case "VERIFIED" -> verifiedSelfie++;
                case "PENDING" -> pendingSelfie++;
                case "REJECTED" -> rejectedSelfie++;
                default -> unverifiedSelfie++;
            }
            if (p != null) {
                int photoCount = extractProfilePhotos(p).size();
                if (photoCount >= 2) twoOrMorePhotos++;
                if (p.getCity() != null && !p.getCity().isBlank()) {
                    String city = p.getCity().trim();
                    cityCounts.put(city, cityCounts.getOrDefault(city, 0L) + 1L);
                }
            }
        }

        long digilockerVerified = allUsers.stream().filter(u -> Boolean.TRUE.equals(u.getDigilockerVerified())).count();
        long activeVipPasses = allUsers.stream().filter(u -> Boolean.TRUE.equals(u.getHasActivePass())).count();
        long shadowbannedCount = allUsers.stream().filter(u -> Boolean.TRUE.equals(u.getIsIncognito())).count();

        long totalMatches = matchRepository.count();
        long totalMessages = chatMessageRepository.count();

        List<UpiOrder> allOrders = upiOrderRepository.findAll();
        long capturedOrders = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.CAPTURED).count();
        long totalRevenuePaise = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CAPTURED && o.getAmountPaise() != null)
                .mapToLong(UpiOrder::getAmountPaise)
                .sum();

        List<SupportTicket> tickets = supportTicketRepository.findAll();
        long openTickets = tickets.stream()
                .filter(t -> t.getStatus() == TicketStatus.PENDING || t.getStatus() == TicketStatus.IN_PROGRESS)
                .count();

        List<Map<String, Object>> topCities = cityCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(8)
                .map(e -> Map.<String, Object>of("city", e.getKey(), "users", e.getValue()))
                .toList();

        List<Map<String, Object>> recentUsers = allUsers.stream()
                .sorted((a, b) -> {
                    OffsetDateTime ca = a.getCreatedAt() != null ? a.getCreatedAt() : OffsetDateTime.MIN;
                    OffsetDateTime cb = b.getCreatedAt() != null ? b.getCreatedAt() : OffsetDateTime.MIN;
                    return cb.compareTo(ca);
                })
                .limit(8)
                .map(u -> buildCompactUserRow(u, profileMap.get(u.getId()), true))
                .toList();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalUsers", totalUsers);
        res.put("signups24h", signups24h);
        res.put("signups7d", signups7d);
        res.put("verifiedSelfie", verifiedSelfie);
        res.put("pendingSelfie", pendingSelfie);
        res.put("rejectedSelfie", rejectedSelfie);
        res.put("unverifiedSelfie", unverifiedSelfie);
        res.put("twoOrMorePhotos", twoOrMorePhotos);
        res.put("digilockerVerified", digilockerVerified);
        res.put("activeVipPasses", activeVipPasses);
        res.put("shadowbannedCount", shadowbannedCount);
        res.put("totalMatches", totalMatches);
        res.put("totalMessages", totalMessages);
        res.put("totalOrders", allOrders.size());
        res.put("capturedOrders", capturedOrders);
        res.put("totalRevenueInr", totalRevenuePaise / 100.0);
        res.put("openTickets", openTickets);
        long totalReports = userReportRepository.count();
        long pendingReports = userReportRepository.findByStatusOrderByCreatedAtDesc("PENDING").size();
        long totalBlocks = userBlockRepository.count();

        res.put("totalCircles", microCommunityRepository.count());
        res.put("totalSafeSpots", safeDateSpotRepository.count());
        res.put("totalDeletions", accountDeletionLogRepository.count());
        res.put("totalReports", totalReports);
        res.put("pendingReports", pendingReports);
        res.put("totalBlocks", totalBlocks);
        res.put("topCities", topCities);
        res.put("recentUsers", recentUsers);

        return ResponseEntity.ok(res);
    }

    // =========================================================================
    // 3. MODULE 2: SELFIE & FACEMATCH MODERATION QUEUE (SUPER_ADMIN ONLY)
    // =========================================================================

    @GetMapping("/verification-queue")
    public ResponseEntity<List<Map<String, Object>>> getVerificationQueue(
            @RequestParam(defaultValue = "PENDING") String statusFilter,
            @RequestParam(required = false) String city,
            HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN");

        List<User> users = userRepository.findAll();
        Map<UUID, Profile> profileMap = profileRepository.findAll().stream()
                .collect(Collectors.toMap(Profile::getUserId, p -> p, (a, b) -> a));

        List<Map<String, Object>> queue = new ArrayList<>();
        for (User u : users) {
            Profile p = profileMap.get(u.getId());
            String status = resolveVerificationStatus(u, p);
            boolean hasSelfie = p != null && p.getSelfieUrl() != null && !p.getSelfieUrl().isBlank();

            if ("PENDING".equalsIgnoreCase(statusFilter) && !"PENDING".equals(status)) continue;
            if ("REJECTED".equalsIgnoreCase(statusFilter) && !"REJECTED".equals(status)) continue;
            if ("VERIFIED".equalsIgnoreCase(statusFilter) && !"VERIFIED".equals(status)) continue;
            if ("WITH_SELFIE".equalsIgnoreCase(statusFilter) && !hasSelfie) continue;

            if (city != null && !city.isBlank() && !"ALL".equalsIgnoreCase(city.trim())) {
                String userCity = p != null ? p.getCity() : null;
                if (userCity == null || !userCity.trim().toLowerCase().contains(city.trim().toLowerCase())) {
                    continue;
                }
            }

            Map<String, Object> item = buildCompactUserRow(u, p, true);
            queue.add(item);
        }

        // Prioritize PENDING first, then REJECTED, then users with selfieUrl
        queue.sort((a, b) -> {
            int pa = priorityForStatus((String) a.get("verificationStatus"), (String) a.get("selfieUrl"));
            int pb = priorityForStatus((String) b.get("verificationStatus"), (String) b.get("selfieUrl"));
            return Integer.compare(pa, pb);
        });

        return ResponseEntity.ok(queue);
    }

    private int priorityForStatus(String status, String selfieUrl) {
        if ("PENDING".equals(status)) return 0;
        if ("REJECTED".equals(status)) return 1;
        if (selfieUrl != null && !selfieUrl.isBlank()) return 2;
        if ("VERIFIED".equals(status)) return 3;
        return 4;
    }

    @PostMapping("/verification/{userId}/decision")
    public ResponseEntity<Map<String, Object>> decideVerification(
            @PathVariable UUID userId,
            @RequestBody VerificationDecisionRequest req,
            HttpServletRequest request) {
        AdminSession session = requireAdmin(request, "SUPER_ADMIN");
        String ip = extractClientIp(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Profile profile = profileRepository.findById(userId).orElse(null);

        boolean approve = "APPROVE".equalsIgnoreCase(req.getDecision()) || "VERIFIED".equalsIgnoreCase(req.getDecision());
        String newStatus = approve ? "VERIFIED" : "REJECTED";

        user.setFaceVerified(approve);
        if (approve && (user.getLivenessScore() == null || user.getLivenessScore() < 0.90)) {
            user.setLivenessScore(0.96);
        }
        userRepository.save(user);

        if (profile != null) {
            profile.setVerificationStatus(newStatus);
            profileRepository.save(profile);
        }

        String reason = (req.getReason() != null && !req.getReason().isBlank())
                ? req.getReason().trim()
                : (approve ? "Approved by Trust & Safety team" : "Selfie did not match uploaded profile photos");

        // Send in-app notification to user
        UserNotification notif = UserNotification.builder()
                .userId(userId)
                .type("SYSTEM")
                .title(approve ? "Profile Selfie Verified! ✓" : "Selfie Verification Needs Retake 📸")
                .body(approve
                        ? "Your selfie and profile photos have been verified! You now have the Verified badge."
                        : "Reason: " + reason + ". Please retake a clear, well-lit selfie in your profile.")
                .isRead(false)
                .build();
        notificationRepository.save(notif);

        recordAudit(session.email, session.role, "SELFIE_" + newStatus, userId.toString(), reason, ip);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "verificationStatus", newStatus,
                "faceVerified", approve,
                "reason", reason
        ));
    }

    // =========================================================================
    // 4. MODULE 3: 360° USER DIRECTORY & INSPECTOR
    //    (READ-ONLY FOR SUPPORT_AGENT, FULL CRUD FOR SUPER_ADMIN)
    // =========================================================================

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN", "SUPPORT_AGENT");

        String query = q != null ? q.trim().toLowerCase() : "";
        String cityFilter = city != null ? city.trim().toLowerCase() : "";
        String statusFilter = status != null ? status.trim().toUpperCase() : "";

        List<User> users = userRepository.findAll();
        Map<UUID, Profile> profileMap = profileRepository.findAll().stream()
                .collect(Collectors.toMap(Profile::getUserId, p -> p, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            Profile p = profileMap.get(u.getId());
            String verStatus = resolveVerificationStatus(u, p);

            if (!statusFilter.isEmpty() && !"ALL".equals(statusFilter)) {
                if ("SHADOWBANNED".equals(statusFilter)) {
                    if (!Boolean.TRUE.equals(u.getIsIncognito())) continue;
                } else if ("VIP".equals(statusFilter)) {
                    if (!Boolean.TRUE.equals(u.getHasActivePass())) continue;
                } else if (!statusFilter.equals(verStatus)) {
                    continue;
                }
            }

            if (!cityFilter.isEmpty() && !"all".equals(cityFilter)) {
                String pCity = p != null && p.getCity() != null ? p.getCity().toLowerCase() : "";
                String pCircle = p != null && p.getMicroCircle() != null ? p.getMicroCircle().toLowerCase() : "";
                if (!pCity.contains(cityFilter) && !pCircle.contains(cityFilter)) continue;
            }

            if (!query.isEmpty()) {
                String name = p != null && p.getDisplayName() != null ? p.getDisplayName().toLowerCase() : "";
                String phone = u.getPhoneE164() != null ? u.getPhoneE164().toLowerCase() : "";
                String email = u.getEmail() != null ? u.getEmail().toLowerCase() : "";
                String uid = u.getId().toString().toLowerCase();
                String circle = p != null && p.getMicroCircle() != null ? p.getMicroCircle().toLowerCase() : "";
                if (!name.contains(query) && !phone.contains(query) && !email.contains(query) && !uid.contains(query) && !circle.contains(query)) {
                    continue;
                }
            }

            result.add(buildCompactUserRow(u, p, true));
        }

        result.sort((a, b) -> String.valueOf(b.get("createdAt")).compareTo(String.valueOf(a.get("createdAt"))));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> inspectUser(
            @PathVariable UUID userId,
            HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN", "SUPPORT_AGENT");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Profile profile = profileRepository.findById(userId).orElse(null);
        UserAstrology astro = astrologyRepository.findById(userId).orElse(null);
        List<UpiOrder> orders = upiOrderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<PaymentExecutionLog> paymentLogs = paymentExecutionLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<SupportTicket> tickets = supportTicketRepository.findByUserIdOrderByCreatedAtDesc(userId);
        int shieldedContacts = contactShieldRepository.findByUserId(userId).size();

        Map<String, Object> detail = new LinkedHashMap<>(buildCompactUserRow(user, profile, true));
        if (profile != null) {
            detail.put("bio", profile.getBio());
            detail.put("job", profile.getJob() != null ? profile.getJob() : profile.getOccupation());
            detail.put("institute", profile.getInstitute() != null ? profile.getInstitute() : profile.getEducation());
            detail.put("height", profile.getHeight());
            detail.put("dietaryPref", profile.getDietaryPref() != null ? profile.getDietaryPref().name() : null);
            detail.put("drinkingHabit", profile.getDrinkingHabit());
            detail.put("smokingHabit", profile.getSmokingHabit());
            detail.put("languagesSpoken", profile.getLanguagesSpoken());
            detail.put("genderDisplay", profile.getGenderDisplay());
            detail.put("genderPreferenceDisplay", profile.getGenderPreferenceDisplay());
            detail.put("relationshipIntent", profile.getRelationshipIntent());
            detail.put("photo1", profile.getPhoto1());
            detail.put("photo2", profile.getPhoto2());
            detail.put("photo3", profile.getPhoto3());
            detail.put("photo4", profile.getPhoto4());
            detail.put("photo5", profile.getPhoto5());
            detail.put("photo6", profile.getPhoto6());
        }
        detail.put("astrology", astro);
        detail.put("shieldedContactsCount", shieldedContacts);
        detail.put("orders", orders);
        detail.put("paymentLogs", paymentLogs);
        detail.put("tickets", tickets);

        return ResponseEntity.ok(detail);
    }

    @PostMapping("/users/{userId}/reveal-phone")
    public ResponseEntity<Map<String, String>> revealUserPhone(
            @PathVariable UUID userId,
            HttpServletRequest request) {
        AdminSession session = requireAdmin(request, "SUPER_ADMIN", "SUPPORT_AGENT");
        String ip = extractClientIp(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        recordAudit(session.email, session.role, "PII_PHONE_REVEALED", userId.toString(),
                "Revealed unmasked phone number for user " + userId, ip);

        return ResponseEntity.ok(Map.of(
                "userId", userId.toString(),
                "phoneE164", user.getPhoneE164()
        ));
    }

    @PostMapping("/users/{userId}/action")
    public ResponseEntity<Map<String, Object>> performUserAction(
            @PathVariable UUID userId,
            @RequestBody UserActionRequest req,
            HttpServletRequest request) {
        // Strictly SUPER_ADMIN only — Support Agent cannot modify profiles, approve selfies, or grant credits
        AdminSession session = requireAdmin(request, "SUPER_ADMIN");
        String ip = extractClientIp(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Profile profile = profileRepository.findById(userId).orElse(null);

        String action = req.getAction() != null ? req.getAction().trim().toUpperCase() : "";
        String auditDetail = action;

        switch (action) {
            case "VERIFY_SELFIE" -> {
                user.setFaceVerified(true);
                user.setLivenessScore(0.96);
                if (profile != null) profile.setVerificationStatus("VERIFIED");
                auditDetail = "Marked selfie VERIFIED";
            }
            case "REJECT_SELFIE" -> {
                user.setFaceVerified(false);
                if (profile != null) profile.setVerificationStatus("REJECTED");
                auditDetail = "Marked selfie REJECTED";
            }
            case "TOGGLE_DIGILOCKER" -> {
                boolean next = !Boolean.TRUE.equals(user.getDigilockerVerified());
                user.setDigilockerVerified(next);
                auditDetail = "Toggled DigiLocker verified -> " + next;
            }
            case "TOGGLE_SHADOWBAN" -> {
                boolean next = !Boolean.TRUE.equals(user.getIsIncognito());
                user.setIsIncognito(next);
                auditDetail = (next ? "Shadowbanned user (hidden from discovery)" : "Removed shadowban");
            }
            case "GRANT_CREDITS" -> {
                if (req.getSparksDelta() != null) {
                    user.setSparksBalance(Math.max(0, (user.getSparksBalance() != null ? user.getSparksBalance() : 0) + req.getSparksDelta()));
                }
                if (req.getBoostsDelta() != null) {
                    user.setBoostsBalance(Math.max(0, (user.getBoostsBalance() != null ? user.getBoostsBalance() : 0) + req.getBoostsDelta()));
                }
                if (req.getDirectDmsDelta() != null) {
                    user.setDirectDmsBalance(Math.max(0, (user.getDirectDmsBalance() != null ? user.getDirectDmsBalance() : 0) + req.getDirectDmsDelta()));
                }
                if (req.getGrantVipPass() != null) {
                    user.setHasActivePass(req.getGrantVipPass());
                    int days = (req.getVipDays() != null && req.getVipDays() > 0) ? req.getVipDays() : 30;
                    if (req.getGrantVipPass()) {
                        OffsetDateTime base = (user.getPassExpiry() != null && user.getPassExpiry().isAfter(OffsetDateTime.now()))
                                ? user.getPassExpiry()
                                : OffsetDateTime.now();
                        user.setPassExpiry(base.plusDays(days));
                    } else {
                        user.setPassExpiry(null);
                    }
                }
                auditDetail = String.format("Updated credits: sparks=%d, boosts=%d, dms=%d, vip=%s (expiry: %s)",
                        user.getSparksBalance(), user.getBoostsBalance(), user.getDirectDmsBalance(), user.getHasActivePass(),
                        (user.getPassExpiry() != null ? user.getPassExpiry().toString() : "none"));
            }
            case "GRANT_MEMBERSHIP" -> {
                String planTitle = "Custom VIP Membership";
                int daysToAdd = (req.getVipDays() != null && req.getVipDays() > 0) ? req.getVipDays() : 30;
                int sparksToAdd = (req.getSparksDelta() != null) ? req.getSparksDelta() : 0;
                int boostsToAdd = (req.getBoostsDelta() != null) ? req.getBoostsDelta() : 0;
                int dmsToAdd = (req.getDirectDmsDelta() != null) ? req.getDirectDmsDelta() : 0;
                int karmaToAdd = (req.getKarmaDelta() != null) ? req.getKarmaDelta() : 0;

                String skuStr = req.getMembershipSku() != null ? req.getMembershipSku().trim().toUpperCase() : "";
                if (!skuStr.isEmpty() && !skuStr.equals("CUSTOM")) {
                    try {
                        SkuType sku = SkuType.valueOf(skuStr);
                        planTitle = sku.getDescription();
                        switch (sku) {
                            case WEEKEND_PASS_79, WEEKEND_PASS_99 -> {
                                daysToAdd = 3;
                                sparksToAdd = Math.max(sparksToAdd, 3);
                            }
                            case WINGMAN_BUNDLE_199 -> {
                                daysToAdd = 3;
                                sparksToAdd = Math.max(sparksToAdd, 5);
                                boostsToAdd = Math.max(boostsToAdd, 1);
                                dmsToAdd = Math.max(dmsToAdd, 2);
                            }
                            case WEEKLY_PASS_149 -> {
                                daysToAdd = 7;
                                sparksToAdd = Math.max(sparksToAdd, 5);
                                boostsToAdd = Math.max(boostsToAdd, 1);
                                dmsToAdd = Math.max(dmsToAdd, 3);
                            }
                            case FORTNIGHT_PASS_199 -> {
                                daysToAdd = 14;
                                sparksToAdd = Math.max(sparksToAdd, 6);
                                boostsToAdd = Math.max(boostsToAdd, 2);
                                dmsToAdd = Math.max(dmsToAdd, 5);
                            }
                            case MONTHLY_PASS_349 -> {
                                daysToAdd = 30;
                                sparksToAdd = Math.max(sparksToAdd, 15);
                                boostsToAdd = Math.max(boostsToAdd, 4);
                                dmsToAdd = Math.max(dmsToAdd, 10);
                                karmaToAdd = Math.max(karmaToAdd, 10);
                            }
                            case SELECT_QUARTERLY_899, SELECT_QUARTERLY_999 -> {
                                daysToAdd = 90;
                                sparksToAdd = Math.max(sparksToAdd, 25);
                                dmsToAdd = Math.max(dmsToAdd, 25);
                                karmaToAdd = Math.max(karmaToAdd, 20);
                            }
                            case SUPER_SPARK_19, CUTTING_CHAI_21 -> {
                                daysToAdd = 0;
                                sparksToAdd = Math.max(sparksToAdd, 1);
                            }
                            case SPARKS_PACK_5_79 -> {
                                daysToAdd = 0;
                                sparksToAdd = Math.max(sparksToAdd, 5);
                            }
                            case BOOST_1X_FRIDAY_29, BOOST_1X_FRIDAY_39 -> {
                                daysToAdd = 0;
                                boostsToAdd = Math.max(boostsToAdd, 1);
                            }
                            case DIRECT_DMS_3X_49, DIRECT_DMS_3X_89 -> {
                                daysToAdd = 0;
                                dmsToAdd = Math.max(dmsToAdd, 3);
                            }
                            case REVIVE_MATCH_19 -> {
                                daysToAdd = 0;
                            }
                        }
                    } catch (Exception e) {
                        planTitle = skuStr;
                    }
                } else if (req.getCustomPlanName() != null && !req.getCustomPlanName().isBlank()) {
                    planTitle = req.getCustomPlanName().trim();
                }

                if (daysToAdd > 0) {
                    user.setHasActivePass(true);
                    OffsetDateTime base = (user.getPassExpiry() != null && user.getPassExpiry().isAfter(OffsetDateTime.now()))
                            ? user.getPassExpiry()
                            : OffsetDateTime.now();
                    user.setPassExpiry(base.plusDays(daysToAdd));
                }

                if (sparksToAdd != 0) {
                    user.setSparksBalance(Math.max(0, (user.getSparksBalance() != null ? user.getSparksBalance() : 0) + sparksToAdd));
                }
                if (boostsToAdd != 0) {
                    user.setBoostsBalance(Math.max(0, (user.getBoostsBalance() != null ? user.getBoostsBalance() : 0) + boostsToAdd));
                }
                if (dmsToAdd != 0) {
                    user.setDirectDmsBalance(Math.max(0, (user.getDirectDmsBalance() != null ? user.getDirectDmsBalance() : 0) + dmsToAdd));
                }
                if (karmaToAdd != 0) {
                    user.setKarmaScore(Math.min(200, Math.max(0, (user.getKarmaScore() != null ? user.getKarmaScore() : 100) + karmaToAdd)));
                }

                String reason = (req.getReason() != null && !req.getReason().isBlank()) ? req.getReason() : "Admin manual grant";
                auditDetail = String.format("Granted VIP: '%s' (+%d days VIP till %s, +%d sparks, +%d boosts, +%d dms, +%d karma). Reason: %s",
                        planTitle, daysToAdd, (user.getPassExpiry() != null ? user.getPassExpiry().toString() : "N/A"),
                        sparksToAdd, boostsToAdd, dmsToAdd, karmaToAdd, reason);
            }
            case "UPDATE_KARMA" -> {
                if (req.getKarmaScore() != null) {
                    int clamped = Math.max(0, Math.min(200, req.getKarmaScore()));
                    user.setKarmaScore(clamped);
                    auditDetail = "Updated Karma score to " + clamped;
                }
            }
            case "REMOVE_PHOTO" -> {
                if (profile != null && req.getPhotoSlot() != null) {
                    int slot = req.getPhotoSlot();
                    switch (slot) {
                        case 1 -> profile.setPhoto1(null);
                        case 2 -> profile.setPhoto2(null);
                        case 3 -> profile.setPhoto3(null);
                        case 4 -> profile.setPhoto4(null);
                        case 5 -> profile.setPhoto5(null);
                        case 6 -> profile.setPhoto6(null);
                    }
                    List<String> remaining = extractSlotOnlyPhotos(profile);
                    try {
                        profile.setPhotosJson(objectMapper.writeValueAsString(remaining));
                    } catch (Exception ignored) {}
                    auditDetail = "Removed photo in slot #" + slot;
                }
            }
            case "UPDATE_MARKETING_EMAIL" -> {
                if (req.getEmail() != null) {
                    String cleanEmail = req.getEmail().trim().toLowerCase();
                    user.setEmail(cleanEmail.isEmpty() ? null : cleanEmail);
                }
                if (req.getMarketingOptIn() != null) {
                    user.setMarketingOptIn(req.getMarketingOptIn());
                    if (Boolean.TRUE.equals(req.getMarketingOptIn())) {
                        user.setMarketingOptInAt(OffsetDateTime.now());
                    }
                }
                auditDetail = String.format("Updated marketing email=%s, optIn=%s", user.getEmail(), user.getMarketingOptIn());
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown action: " + action);
        }

        userRepository.save(user);
        if (profile != null) {
            profileRepository.save(profile);
        }

        recordAudit(session.email, session.role, "USER_ACTION_" + action, userId.toString(), auditDetail, ip);

        return ResponseEntity.ok(buildCompactUserRow(user, profile, true));
    }

    // =========================================================================
    // 5. MODULE 4: TRUST, SAFETY & SHADOWSHIELD CENTER (SUPER_ADMIN ONLY)
    // =========================================================================

    @GetMapping("/safety")
    public ResponseEntity<Map<String, Object>> getSafetyOverview(HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN");

        List<User> users = userRepository.findAll();
        Map<UUID, Profile> profileMap = profileRepository.findAll().stream()
                .collect(Collectors.toMap(Profile::getUserId, p -> p, (a, b) -> a));

        List<Map<String, Object>> flaggedUsers = users.stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsIncognito())
                        || (u.getKarmaScore() != null && u.getKarmaScore() < 85))
                .map(u -> buildCompactUserRow(u, profileMap.get(u.getId()), true))
                .toList();

        List<UserContactShield> shields = contactShieldRepository.findAll();
        long uniqueUsersUsingShield = shields.stream().map(UserContactShield::getUserId).distinct().count();
        Set<String> corporateDomains = shields.stream()
                .map(UserContactShield::getCorporateDomain)
                .filter(d -> d != null && !d.isBlank())
                .collect(Collectors.toSet());

        List<SupportTicket> safetyTickets = supportTicketRepository.findAll().stream()
                .filter(t -> t.getCategory() != null &&
                        (t.getCategory().name().contains("SAFETY") || t.getCategory().name().contains("VERIFICATION")))
                .sorted((a, b) -> {
                    OffsetDateTime ca = a.getCreatedAt() != null ? a.getCreatedAt() : OffsetDateTime.MIN;
                    OffsetDateTime cb = b.getCreatedAt() != null ? b.getCreatedAt() : OffsetDateTime.MIN;
                    return cb.compareTo(ca);
                })
                .toList();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalShieldedHashes", shields.size());
        res.put("uniqueUsersUsingShield", uniqueUsersUsingShield);
        res.put("corporateDomainsBlocked", corporateDomains);
        res.put("flaggedUsers", flaggedUsers);
        res.put("safetyTickets", safetyTickets);
        res.put("totalReportsCount", userReportRepository.count());
        res.put("pendingReportsCount", userReportRepository.countAllReportsGroupedByReportedUser().size());
        return ResponseEntity.ok(res);
    }

    // =========================================================================
    // 5B. MODULE 4B: REPORTED PROFILES DESK & ACTION CENTER (TRUST & SAFETY)
    // =========================================================================

    @GetMapping("/reports")
    public ResponseEntity<List<Map<String, Object>>> getReportedProfiles(
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN", "SUPPORT_AGENT");

        List<UserReport> allReports = (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status))
                ? userReportRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase())
                : userReportRepository.findAll();

        allReports.sort((a, b) -> {
            OffsetDateTime ca = a.getCreatedAt() != null ? a.getCreatedAt() : OffsetDateTime.MIN;
            OffsetDateTime cb = b.getCreatedAt() != null ? b.getCreatedAt() : OffsetDateTime.MIN;
            return cb.compareTo(ca);
        });

        // Group by reportedUserId
        Map<UUID, List<UserReport>> grouped = allReports.stream()
                .collect(Collectors.groupingBy(UserReport::getReportedUserId, LinkedHashMap::new, Collectors.toList()));

        Map<UUID, Profile> profileMap = profileRepository.findAllById(grouped.keySet()).stream()
                .collect(Collectors.toMap(Profile::getUserId, p -> p, (a, b) -> a));
        Map<UUID, User> userMap = userRepository.findAllById(grouped.keySet()).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        Set<UUID> reporterIds = allReports.stream().map(UserReport::getReporterId).collect(Collectors.toSet());
        Map<UUID, Profile> reporterProfiles = profileRepository.findAllById(reporterIds).stream()
                .collect(Collectors.toMap(Profile::getUserId, p -> p, (a, b) -> a));
        Map<UUID, User> reporterUsers = userRepository.findAllById(reporterIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<UUID, List<UserReport>> entry : grouped.entrySet()) {
            UUID reportedUserId = entry.getKey();
            List<UserReport> userReports = entry.getValue();

            User reportedUser = userMap.get(reportedUserId);
            Profile reportedProfile = profileMap.get(reportedUserId);

            long pendingCount = userReports.stream().filter(r -> "PENDING".equalsIgnoreCase(r.getStatus())).count();

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("reportedUserId", reportedUserId);
            card.put("displayName", (reportedProfile != null && reportedProfile.getDisplayName() != null) ? reportedProfile.getDisplayName().trim() : "Member");
            card.put("phoneMasked", reportedUser != null ? maskPhone(reportedUser.getPhoneE164()) : "—");
            card.put("city", (reportedProfile != null && reportedProfile.getCity() != null) ? reportedProfile.getCity().trim() : "—");
            card.put("isSuspended", reportedUser != null && Boolean.TRUE.equals(reportedUser.getIsIncognito()));
            card.put("karmaScore", reportedUser != null && reportedUser.getKarmaScore() != null ? reportedUser.getKarmaScore() : 100);
            card.put("primaryPhoto", reportedProfile != null ? extractPrimaryPhoto(reportedProfile) : null);
            card.put("totalReportsCount", userReports.size());
            card.put("pendingReportsCount", pendingCount);
            card.put("latestReportAt", userReports.get(0).getCreatedAt());

            List<Map<String, Object>> reportsList = new ArrayList<>();
            for (UserReport r : userReports) {
                Profile repProf = reporterProfiles.get(r.getReporterId());
                User repUser = reporterUsers.get(r.getReporterId());

                Map<String, Object> rMap = new LinkedHashMap<>();
                rMap.put("id", r.getId());
                rMap.put("reporterId", r.getReporterId());
                rMap.put("reporterName", (repProf != null && repProf.getDisplayName() != null) ? repProf.getDisplayName().trim() : "Anonymous User");
                rMap.put("reporterPhoneMasked", repUser != null ? maskPhone(repUser.getPhoneE164()) : "—");
                rMap.put("matchId", r.getMatchId());
                rMap.put("reason", r.getReason());
                rMap.put("remarks", r.getRemarks());
                rMap.put("status", r.getStatus());
                rMap.put("adminAction", r.getAdminAction());
                rMap.put("adminMessage", r.getAdminMessage());
                rMap.put("resolvedBy", r.getResolvedBy());
                rMap.put("resolvedAt", r.getResolvedAt());
                rMap.put("createdAt", r.getCreatedAt());
                reportsList.add(rMap);
            }
            card.put("reports", reportsList);
            result.add(card);
        }

        result.sort((a, b) -> {
            long pB = (long) b.get("pendingReportsCount");
            long pA = (long) a.get("pendingReportsCount");
            if (pB != pA) return Long.compare(pB, pA);
            return Integer.compare((int) b.get("totalReportsCount"), (int) a.get("totalReportsCount"));
        });

        return ResponseEntity.ok(result);
    }

    @GetMapping("/blocks")
    public ResponseEntity<List<Map<String, Object>>> getBlockedProfiles(HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN", "SUPPORT_AGENT");

        List<UserBlock> allBlocks = userBlockRepository.findAllByOrderByCreatedAtDesc();

        // Group by blockedUserId
        Map<UUID, List<UserBlock>> grouped = allBlocks.stream()
                .collect(Collectors.groupingBy(UserBlock::getBlockedUserId, LinkedHashMap::new, Collectors.toList()));

        Map<UUID, Profile> profileMap = profileRepository.findAllById(grouped.keySet()).stream()
                .collect(Collectors.toMap(Profile::getUserId, p -> p, (a, b) -> a));
        Map<UUID, User> userMap = userRepository.findAllById(grouped.keySet()).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        Set<UUID> blockerIds = allBlocks.stream().map(UserBlock::getBlockerId).collect(Collectors.toSet());
        Map<UUID, Profile> blockerProfiles = profileRepository.findAllById(blockerIds).stream()
                .collect(Collectors.toMap(Profile::getUserId, p -> p, (a, b) -> a));
        Map<UUID, User> blockerUsers = userRepository.findAllById(blockerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<UUID, List<UserBlock>> entry : grouped.entrySet()) {
            UUID blockedUserId = entry.getKey();
            List<UserBlock> userBlocks = entry.getValue();

            User blockedUser = userMap.get(blockedUserId);
            Profile blockedProfile = profileMap.get(blockedUserId);

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("blockedUserId", blockedUserId);
            card.put("displayName", (blockedProfile != null && blockedProfile.getDisplayName() != null) ? blockedProfile.getDisplayName().trim() : "Member");
            card.put("phoneMasked", blockedUser != null ? maskPhone(blockedUser.getPhoneE164()) : "—");
            card.put("city", (blockedProfile != null && blockedProfile.getCity() != null) ? blockedProfile.getCity().trim() : "—");
            card.put("isSuspended", blockedUser != null && Boolean.TRUE.equals(blockedUser.getIsIncognito()));
            card.put("karmaScore", blockedUser != null && blockedUser.getKarmaScore() != null ? blockedUser.getKarmaScore() : 100);
            card.put("primaryPhoto", blockedProfile != null ? extractPrimaryPhoto(blockedProfile) : null);
            card.put("totalBlocksCount", userBlocks.size());
            card.put("latestBlockedAt", userBlocks.get(0).getCreatedAt());

            List<Map<String, Object>> blocksList = new ArrayList<>();
            for (UserBlock b : userBlocks) {
                Profile bProf = blockerProfiles.get(b.getBlockerId());
                User bUser = blockerUsers.get(b.getBlockerId());

                Map<String, Object> bMap = new LinkedHashMap<>();
                bMap.put("id", b.getId());
                bMap.put("blockerId", b.getBlockerId());
                bMap.put("blockerName", (bProf != null && bProf.getDisplayName() != null) ? bProf.getDisplayName().trim() : "Anonymous User");
                bMap.put("blockerPhoneMasked", bUser != null ? maskPhone(bUser.getPhoneE164()) : "—");
                bMap.put("reason", b.getReason() != null ? b.getReason() : "Direct Block");
                bMap.put("remarks", b.getRemarks());
                bMap.put("createdAt", b.getCreatedAt());
                blocksList.add(bMap);
            }
            card.put("blocks", blocksList);
            result.add(card);
        }

        result.sort((a, b) -> Integer.compare((int) b.get("totalBlocksCount"), (int) a.get("totalBlocksCount")));

        return ResponseEntity.ok(result);
    }

    @PostMapping("/reports/user/{targetUserId}/action")
    public ResponseEntity<Map<String, Object>> takeActionOnReportedUser(
            @PathVariable UUID targetUserId,
            @RequestBody ReportActionRequest req,
            HttpServletRequest request) {
        AdminSession session = requireAdmin(request, "SUPER_ADMIN", "SUPPORT_AGENT");
        String ip = extractClientIp(request);

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found"));
        Profile targetProfile = profileRepository.findById(targetUserId).orElse(null);
        String targetName = (targetProfile != null && targetProfile.getDisplayName() != null)
                ? targetProfile.getDisplayName().trim() : "User";

        String action = req.getAction() != null ? req.getAction().trim().toUpperCase() : "WARNING_ISSUED";
        String adminMsg = req.getAdminMessage() != null ? req.getAdminMessage().trim() : "";
        if (adminMsg.isBlank()) {
            adminMsg = switch (action) {
                case "PROFILE_SUSPENDED" -> "Thank you for reporting. We have reviewed your report against " + targetName + " and suspended this profile for violating our community guidelines.";
                case "RESTORE_PROFILE", "UNBAN" -> "Your report has been reviewed. The profile status for " + targetName + " has been reassessed and restored.";
                case "CONTENT_REMOVED" -> "Thank you for reporting. We have reviewed " + targetName + "'s profile and removed the inappropriate content.";
                case "WARNING_ISSUED" -> "Thank you for reporting. A formal warning and karma penalty have been issued to " + targetName + ".";
                case "DISMISSED" -> "Thank you for your report. After thorough review, no community guideline violation was detected at this time.";
                default -> "Our Trust & Safety team has processed your report regarding " + targetName + ". Action taken: " + action + ".";
            };
        }

        // If suspendAccount or action == PROFILE_SUSPENDED, shadowban the target user
        if ("RESTORE_PROFILE".equals(action) || "UNBAN".equals(action)) {
            targetUser.setIsIncognito(false);
            userRepository.save(targetUser);
        } else if (Boolean.TRUE.equals(req.getSuspendAccount()) || "PROFILE_SUSPENDED".equals(action)) {
            targetUser.setIsIncognito(true);
            int currentKarma = targetUser.getKarmaScore() != null ? targetUser.getKarmaScore() : 100;
            targetUser.setKarmaScore(Math.max(0, currentKarma - 50));
            userRepository.save(targetUser);
        }

        // Update UserReport records
        List<UserReport> reportsToUpdate = new ArrayList<>();
        if (req.getReportId() != null) {
            userReportRepository.findById(req.getReportId()).ifPresent(reportsToUpdate::add);
        } else {
            List<UserReport> existing = userReportRepository.findByReportedUserIdOrderByCreatedAtDesc(targetUserId);
            for (UserReport r : existing) {
                if ("PENDING".equalsIgnoreCase(r.getStatus())) {
                    reportsToUpdate.add(r);
                }
            }
            if (reportsToUpdate.isEmpty()) {
                reportsToUpdate.addAll(existing);
            }
        }

        String reportStatus = "DISMISSED".equals(action) ? "DISMISSED" : "RESOLVED";
        OffsetDateTime now = OffsetDateTime.now();
        Set<UUID> notifiedReporters = new HashSet<>();

        for (UserReport report : reportsToUpdate) {
            report.setStatus(reportStatus);
            report.setAdminAction(action);
            report.setAdminMessage(adminMsg);
            report.setResolvedBy(session.email);
            report.setResolvedAt(now);
            userReportRepository.save(report);

            if (!Boolean.FALSE.equals(req.getNotifyReporter()) && report.getReporterId() != null) {
                UUID reporterId = report.getReporterId();
                if (notifiedReporters.add(reporterId)) {
                    try {
                        Map<String, Object> dataMap = new LinkedHashMap<>();
                        dataMap.put("reportId", report.getId());
                        dataMap.put("action", action);
                        dataMap.put("adminMessage", adminMsg);
                        dataMap.put("reportedUserId", targetUserId);
                        dataMap.put("reportedUserName", targetName);
                        dataMap.put("resolvedAt", now.toString());

                        UserNotification notif = UserNotification.builder()
                                .userId(reporterId)
                                .type("REPORT_ACTION")
                                .title("Safety Update: Action Taken on Report 🛡️")
                                .body(adminMsg)
                                .dataJson(objectMapper.writeValueAsString(dataMap))
                                .isRead(false)
                                .build();
                        notificationRepository.save(notif);
                    } catch (Exception ex) {
                        log.warn("Failed to create UserNotification for reporter {}: {}", reporterId, ex.getMessage());
                    }
                }
            }
        }

        recordAudit(session.email, session.role, "REPORT_ACTION_" + action, targetUserId.toString(),
                "Action=" + action + " | Updated=" + reportsToUpdate.size() + " reports | Notified=" + notifiedReporters.size() + " reporters | Msg=" + adminMsg, ip);

        return ResponseEntity.ok(Map.of(
                "targetUserId", targetUserId,
                "action", action,
                "adminMessage", adminMsg,
                "updatedReportsCount", reportsToUpdate.size(),
                "notifiedReportersCount", notifiedReporters.size(),
                "accountSuspended", Boolean.TRUE.equals(targetUser.getIsIncognito())
        ));
    }

    // =========================================================================
    // 6. MODULE 5: PAYMENTS, REVENUE & AUDIT TIMELINE
    //    (VIEW FOR SUPPORT_AGENT, RECONCILE CRUD FOR SUPER_ADMIN ONLY)
    // =========================================================================

    @GetMapping("/payments")
    public ResponseEntity<Map<String, Object>> getPaymentsOverview(HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN", "SUPPORT_AGENT");

        List<UpiOrder> orders = upiOrderRepository.findAll();
        orders.sort((a, b) -> {
            OffsetDateTime ca = a.getCreatedAt() != null ? a.getCreatedAt() : OffsetDateTime.MIN;
            OffsetDateTime cb = b.getCreatedAt() != null ? b.getCreatedAt() : OffsetDateTime.MIN;
            return cb.compareTo(ca);
        });

        long capturedCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.CAPTURED).count();
        long pendingCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count();
        long failedCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.FAILED).count();
        long totalPaise = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CAPTURED && o.getAmountPaise() != null)
                .mapToLong(UpiOrder::getAmountPaise)
                .sum();

        List<PaymentExecutionLog> execLogs = paymentExecutionLogRepository.findTop50ByOrderByCreatedAtDesc();

        Map<UUID, Profile> profileMap = profileRepository.findAll().stream()
                .collect(Collectors.toMap(Profile::getUserId, p -> p, (a, b) -> a));
        Map<UUID, User> userMap = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<Map<String, Object>> orderRows = new ArrayList<>();
        for (UpiOrder o : orders.stream().limit(100).toList()) {
            Map<String, Object> om = new LinkedHashMap<>();
            om.put("id", o.getId());
            om.put("orderId", o.getOrderId());
            om.put("userId", o.getUserId());
            Profile p = profileMap.get(o.getUserId());
            User u = userMap.get(o.getUserId());
            om.put("userName", (p != null && p.getDisplayName() != null && !p.getDisplayName().isBlank()) ? p.getDisplayName().trim() : "Unspecified");
            om.put("userPhoneMasked", u != null ? maskPhone(u.getPhoneE164()) : "—");
            om.put("city", (p != null && p.getCity() != null && !p.getCity().isBlank()) ? p.getCity().trim() : "Not specified");
            om.put("sku", o.getSku());
            om.put("amountPaise", o.getAmountPaise());
            om.put("amountInr", o.getAmountPaise() != null ? o.getAmountPaise() / 100.0 : 0.0);
            om.put("currency", o.getCurrency());
            om.put("status", o.getStatus() != null ? o.getStatus().name() : "PENDING");
            om.put("paymentProvider", o.getPaymentProvider() != null ? o.getPaymentProvider().name() : "UPI");
            om.put("failureReason", o.getFailureReason());
            om.put("adminNotes", o.getAdminNotes());
            om.put("reviewedBy", o.getReviewedBy());
            om.put("createdAt", o.getCreatedAt());
            om.put("capturedAt", o.getCapturedAt());
            orderRows.add(om);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalRevenueInr", totalPaise / 100.0);
        res.put("totalOrders", orders.size());
        res.put("capturedCount", capturedCount);
        res.put("pendingCount", pendingCount);
        res.put("failedCount", failedCount);
        res.put("orders", orderRows);
        res.put("executionLogs", execLogs);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/payments/{orderId}")
    public ResponseEntity<Map<String, Object>> getPaymentDetails(
            @PathVariable String orderId,
            HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN", "SUPPORT_AGENT");

        UpiOrder order = upiOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found: " + orderId));

        User user = userRepository.findById(order.getUserId()).orElse(null);
        Profile profile = profileRepository.findById(order.getUserId()).orElse(null);

        PaymentDto.PaymentAuditTimelineDto timeline = null;
        try {
            timeline = paymentAuditService.getOrderAuditTimeline(orderId);
        } catch (Exception ignored) {}

        List<PaymentExecutionLog> execLogs = paymentExecutionLogRepository.findByOrderIdOrderByCreatedAtDesc(orderId);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("orderId", order.getOrderId());
        res.put("userId", order.getUserId());
        res.put("userName", (profile != null && profile.getDisplayName() != null && !profile.getDisplayName().isBlank()) ? profile.getDisplayName().trim() : "Unspecified");
        res.put("userPhoneMasked", user != null ? maskPhone(user.getPhoneE164()) : "—");
        res.put("city", (profile != null && profile.getCity() != null && !profile.getCity().isBlank()) ? profile.getCity().trim() : "Not specified");
        res.put("sku", order.getSku());
        res.put("amountPaise", order.getAmountPaise());
        res.put("amountInr", order.getAmountPaise() != null ? order.getAmountPaise() / 100.0 : 0.0);
        res.put("currency", order.getCurrency());
        res.put("status", order.getStatus() != null ? order.getStatus().name() : "PENDING");
        res.put("paymentProvider", order.getPaymentProvider() != null ? order.getPaymentProvider().name() : "UPI");
        res.put("failureReason", order.getFailureReason());
        res.put("adminNotes", order.getAdminNotes());
        res.put("reviewedBy", order.getReviewedBy());
        res.put("createdAt", order.getCreatedAt());
        res.put("capturedAt", order.getCapturedAt());
        res.put("timeline", timeline);
        res.put("executionLogs", execLogs);

        return ResponseEntity.ok(res);
    }

    @PostMapping("/payments/{orderId}/reconcile")
    public ResponseEntity<PaymentDto.PaymentAuditTimelineDto> reconcileOrder(
            @PathVariable String orderId,
            @RequestBody PaymentDto.ReviewOrderRequest req,
            HttpServletRequest request) {
        AdminSession session = requireAdmin(request, "SUPER_ADMIN", "SUPPORT_AGENT");
        String ip = extractClientIp(request);

        if (req.getStatus() == null) {
            req.setStatus(OrderStatus.CAPTURED);
            req.setGrantPerks(true);
        }
        if (req.getAdminIdOrName() == null || req.getAdminIdOrName().isBlank()) {
            req.setAdminIdOrName(session.email);
        }
        PaymentDto.PaymentAuditTimelineDto timeline = paymentAuditService.reviewAndReconcileOrder(
                orderId, req, UUID.nameUUIDFromBytes(session.email.getBytes(StandardCharsets.UTF_8)));

        recordAudit(session.email, session.role, "PAYMENT_RECONCILE", orderId,
                "Status=" + req.getStatus() + ", GrantPerks=" + req.isGrantPerks() + ", Notes=" + req.getAdminNotes(), ip);

        return ResponseEntity.ok(timeline);
    }

    // =========================================================================
    // 7. MODULE 6: SUPPORT TICKET DESK (SUPER_ADMIN & SUPPORT_AGENT)
    // =========================================================================

    @GetMapping("/tickets")
    public ResponseEntity<List<Map<String, Object>>> getAllTickets(
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN", "SUPPORT_AGENT");

        Map<UUID, Profile> profileMap = profileRepository.findAll().stream()
                .collect(Collectors.toMap(Profile::getUserId, p -> p, (a, b) -> a));
        Map<UUID, User> userMap = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<SupportTicket> tickets = supportTicketRepository.findAll();
        tickets.sort((a, b) -> {
            OffsetDateTime ca = a.getCreatedAt() != null ? a.getCreatedAt() : OffsetDateTime.MIN;
            OffsetDateTime cb = b.getCreatedAt() != null ? b.getCreatedAt() : OffsetDateTime.MIN;
            return cb.compareTo(ca);
        });

        List<Map<String, Object>> out = new ArrayList<>();
        for (SupportTicket t : tickets) {
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                if (t.getStatus() == null || !t.getStatus().name().equalsIgnoreCase(status)) continue;
            }
            Profile p = profileMap.get(t.getUserId());
            User u = userMap.get(t.getUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("ticketNumber", t.getTicketNumber());
            m.put("userId", t.getUserId());
            m.put("userName", (p != null && p.getDisplayName() != null && !p.getDisplayName().isBlank()) ? p.getDisplayName().trim() : "Unspecified");
            m.put("userPhoneMasked", u != null ? maskPhone(u.getPhoneE164()) : "—");
            m.put("category", t.getCategory());
            m.put("status", t.getStatus());
            m.put("subject", t.getSubject());
            m.put("description", t.getDescription());
            m.put("resolutionNotes", t.getResolutionNotes());
            m.put("remarksHistory", parseRemarksHistory(t.getResolutionNotes()));
            m.put("createdAt", t.getCreatedAt());
            m.put("resolvedAt", t.getResolvedAt());
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping("/tickets/{ticketId}/status")
    public ResponseEntity<SupportTicket> updateTicketStatus(
            @PathVariable UUID ticketId,
            @RequestBody TicketStatusUpdateRequest req,
            HttpServletRequest request) {
        AdminSession session = requireAdmin(request, "SUPER_ADMIN", "SUPPORT_AGENT");
        String ip = extractClientIp(request);

        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            TicketStatus nextStatus = TicketStatus.valueOf(req.getStatus().trim().toUpperCase());
            ticket.setStatus(nextStatus);
            if (nextStatus == TicketStatus.RESOLVED || nextStatus == TicketStatus.CLOSED) {
                ticket.setResolvedAt(OffsetDateTime.now());
            }
        }

        if (req.getResolutionNotes() != null && !req.getResolutionNotes().isBlank()) {
            String noteText = req.getResolutionNotes().trim();
            String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            String statusTag = ticket.getStatus() != null ? ticket.getStatus().name() : "IN_PROGRESS";
            String entry = "[" + timestamp + " • " + session.email + " (" + statusTag + ")]: " + noteText;

            String existing = ticket.getResolutionNotes();
            if (existing != null && !existing.isBlank()) {
                ticket.setResolutionNotes(existing + "\n\n" + entry);
            } else {
                ticket.setResolutionNotes(entry);
            }
        }
        supportTicketRepository.save(ticket);

        // Notify user
        UserNotification notif = UserNotification.builder()
                .userId(ticket.getUserId())
                .type("SYSTEM")
                .title("Support Ticket " + ticket.getTicketNumber() + " Updated (" + ticket.getStatus() + ")")
                .body(req.getResolutionNotes() != null && !req.getResolutionNotes().isBlank()
                        ? req.getResolutionNotes().trim()
                        : "Your support request status is now " + ticket.getStatus())
                .isRead(false)
                .build();
        notificationRepository.save(notif);

        recordAudit(session.email, session.role, "TICKET_UPDATED", ticket.getTicketNumber(),
                "Status=" + ticket.getStatus() + " | Notes=" + req.getResolutionNotes(), ip);

        return ResponseEntity.ok(ticket);
    }

    private List<Map<String, String>> parseRemarksHistory(String notes) {
        List<Map<String, String>> list = new ArrayList<>();
        if (notes == null || notes.isBlank()) return list;

        String[] parts = notes.split("\n\n|---");
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            Map<String, String> item = new LinkedHashMap<>();
            if (p.startsWith("[") && p.contains("]:")) {
                int metaEnd = p.indexOf("]:");
                String meta = p.substring(1, metaEnd);
                String comment = p.substring(metaEnd + 2).trim();
                item.put("meta", meta);
                item.put("comment", comment);
                item.put("raw", p);
            } else {
                item.put("meta", "Recorded Note");
                item.put("comment", p);
                item.put("raw", p);
            }
            list.add(item);
        }
        return list;
    }

    // =========================================================================
    // 8. MODULE 7: CONTENT & GROWTH CMS (SUPER_ADMIN ONLY)
    // =========================================================================

    @GetMapping("/cms/circles")
    public ResponseEntity<List<Map<String, Object>>> getCircles(HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN");
        List<MicroCommunity> list = microCommunityRepository.findAll();
        List<Profile> allProfiles = profileRepository.findAll();

        Map<String, Long> circleCounts = new HashMap<>();
        for (Profile p : allProfiles) {
            if (p.getMicroCircle() != null && !p.getMicroCircle().isBlank()) {
                String c = p.getMicroCircle().trim().toLowerCase();
                circleCounts.put(c, circleCounts.getOrDefault(c, 0L) + 1L);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (MicroCommunity c : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("city", c.getCity());
            m.put("name", c.getName());
            m.put("slug", c.getSlug());
            m.put("tagline", c.getTagline());
            m.put("vibeCategory", c.getVibeCategory());
            m.put("badgeIcon", c.getBadgeIcon());
            m.put("isPopular", Boolean.TRUE.equals(c.getIsPopular()));

            String cName = c.getName().toLowerCase();
            long count = circleCounts.entrySet().stream()
                    .filter(e -> e.getKey().contains(cName) || cName.contains(e.getKey()))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
            m.put("activeMembersCount", count);
            result.add(m);
        }

        result.sort(Comparator.comparing((Map<String, Object> m) -> (String) m.get("city"), Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(m -> (String) m.get("name"), Comparator.nullsLast(String::compareToIgnoreCase)));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/cms/circles")
    public ResponseEntity<MicroCommunity> createCircle(
            @RequestBody CreateCircleRequest req,
            HttpServletRequest request) {
        AdminSession session = requireAdmin(request, "SUPER_ADMIN");
        String ip = extractClientIp(request);

        if (req.getName() == null || req.getName().isBlank() || req.getCity() == null || req.getCity().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "City and Circle Name are required");
        }

        String slug = (req.getCity() + "-" + req.getName())
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "") + "-" + (System.currentTimeMillis() % 1000);

        MicroCommunity circle = MicroCommunity.builder()
                .city(req.getCity().trim())
                .name(req.getName().trim())
                .slug(slug)
                .tagline(req.getTagline() != null && !req.getTagline().isBlank()
                        ? req.getTagline().trim()
                        : "Verified hyper-local community in " + req.getCity().trim())
                .vibeCategory(req.getVibeCategory() != null ? req.getVibeCategory().trim() : "Neighborhood & Lifestyle")
                .badgeIcon(req.getBadgeIcon() != null ? req.getBadgeIcon().trim() : "📍")
                .iconName("location")
                .isPopular(req.getIsPopular() != null ? req.getIsPopular() : true)
                .activeMembersCount(req.getActiveMembersCount() != null ? req.getActiveMembersCount() : 0)
                .build();

        MicroCommunity saved = microCommunityRepository.save(circle);
        recordAudit(session.email, session.role, "CMS_CREATE_CIRCLE", saved.getId().toString(),
                saved.getCity() + " - " + saved.getName(), ip);

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/cms/circles/{id}")
    public ResponseEntity<Map<String, String>> deleteCircle(
            @PathVariable UUID id,
            HttpServletRequest request) {
        AdminSession session = requireAdmin(request, "SUPER_ADMIN");
        String ip = extractClientIp(request);

        microCommunityRepository.deleteById(id);
        recordAudit(session.email, session.role, "CMS_DELETE_CIRCLE", id.toString(), "Deleted micro-circle", ip);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", id.toString()));
    }

    @GetMapping("/cms/safe-dates")
    public ResponseEntity<List<SafeDateSpot>> getSafeSpots(HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN");
        return ResponseEntity.ok(safeDateSpotRepository.findAll());
    }

    @PostMapping("/cms/safe-dates")
    public ResponseEntity<SafeDateSpot> createSafeSpot(
            @RequestBody CreateSafeSpotRequest req,
            HttpServletRequest request) {
        AdminSession session = requireAdmin(request, "SUPER_ADMIN");
        String ip = extractClientIp(request);

        SafeDateSpot spot = SafeDateSpot.builder()
                .name(req.getName())
                .brand(req.getBrand() != null ? req.getBrand() : "Partner Cafe")
                .address(req.getAddress() != null ? req.getAddress() : req.getCity())
                .city(req.getCity() != null ? req.getCity() : "Bengaluru")
                .neighborhood(req.getNeighborhood() != null ? req.getNeighborhood() : "Central")
                .latitude(req.getLatitude() != null ? req.getLatitude() : 12.9716)
                .longitude(req.getLongitude() != null ? req.getLongitude() : 77.5946)
                .discountPercent(req.getDiscountPercent() != null ? req.getDiscountPercent() : 15)
                .couponCode(req.getCouponCode() != null ? req.getCouponCode() : "BLUNDERR15")
                .sosEnabled(true)
                .build();

        SafeDateSpot saved = safeDateSpotRepository.save(spot);
        recordAudit(session.email, session.role, "CMS_CREATE_SAFE_SPOT", String.valueOf(saved.getId()),
                saved.getName() + " (" + saved.getCity() + ")", ip);

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/cms/safe-dates/{id}")
    public ResponseEntity<Map<String, String>> deleteSafeSpot(
            @PathVariable Long id,
            HttpServletRequest request) {
        AdminSession session = requireAdmin(request, "SUPER_ADMIN");
        String ip = extractClientIp(request);

        safeDateSpotRepository.deleteById(id);
        recordAudit(session.email, session.role, "CMS_DELETE_SAFE_SPOT", String.valueOf(id), "Deleted safe date spot", ip);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", String.valueOf(id)));
    }

    @GetMapping("/memberships")
    public ResponseEntity<Map<String, Object>> listMemberships(HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN", "SUPPORT_AGENT");
        List<PaymentDto.CatalogItemDto> catalog = (upiPaymentService != null) ? upiPaymentService.getCatalog() : List.of();
        List<Map<String, Object>> allSkus = Arrays.stream(SkuType.values()).map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sku", s.name());
            m.put("amountPaise", s.getAmountPaise());
            m.put("amountInr", s.getAmountPaise() / 100);
            m.put("description", s.getDescription());
            m.put("isPass", s.name().contains("PASS") || s.name().contains("BUNDLE") || s.name().contains("SELECT"));
            return m;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "catalog", catalog,
                "skus", allSkus
        ));
    }

    // =========================================================================
    // 8B. MODULE 7B: ACCOUNT DELETION & CHURN AUDIT DESK
    // =========================================================================

    @GetMapping("/deletions")
    public ResponseEntity<Map<String, Object>> listAccountDeletions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String reason,
            HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN", "SUPPORT_AGENT");

        List<com.match.SwipeAI.model.AccountDeletionLog> all = accountDeletionLogRepository.findAllByOrderByCreatedAtDesc();

        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
        long deletions7d = all.stream().filter(l -> l.getCreatedAt() != null && l.getCreatedAt().isAfter(sevenDaysAgo)).count();
        long webOtpCount = all.stream().filter(l -> "WEB_OTP_VERIFIED".equalsIgnoreCase(l.getSource())).count();
        long inAppCount = all.stream().filter(l -> "IN_APP_SETTINGS".equalsIgnoreCase(l.getSource())).count();

        Map<String, Long> reasonCounts = all.stream()
                .collect(Collectors.groupingBy(
                        l -> (l.getReasonCategory() != null && !l.getReasonCategory().isBlank()) ? l.getReasonCategory() : "OTHER",
                        Collectors.counting()
                ));

        List<com.match.SwipeAI.model.AccountDeletionLog> filtered = all.stream()
                .filter(l -> {
                    if (reason != null && !reason.isBlank() && !"ALL".equalsIgnoreCase(reason)) {
                        if (!reason.equalsIgnoreCase(l.getReasonCategory())) return false;
                    }
                    if (q != null && !q.isBlank()) {
                        String query = q.trim().toLowerCase();
                        String phone = l.getPhoneE164() != null ? l.getPhoneE164().toLowerCase() : "";
                        String name = l.getDisplayName() != null ? l.getDisplayName().toLowerCase() : "";
                        String remarks = l.getRemarks() != null ? l.getRemarks().toLowerCase() : "";
                        if (!phone.contains(query) && !name.contains(query) && !remarks.contains(query)) {
                            return false;
                        }
                    }
                    return true;
                }).toList();

        return ResponseEntity.ok(Map.of(
                "totalDeletions", all.size(),
                "deletions7d", deletions7d,
                "webOtpCount", webOtpCount,
                "inAppCount", inAppCount,
                "reasonBreakdown", reasonCounts,
                "logs", filtered
        ));
    }

    @PostMapping("/deletions/{id}/review")
    public ResponseEntity<com.match.SwipeAI.model.AccountDeletionLog> reviewAccountDeletion(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        AdminSession session = requireAdmin(request, "SUPER_ADMIN", "SUPPORT_AGENT");
        String ip = extractClientIp(request);

        com.match.SwipeAI.model.AccountDeletionLog log = accountDeletionLogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deletion record not found."));

        if (body.containsKey("status") && body.get("status") != null && !body.get("status").isBlank()) {
            log.setStatus(body.get("status").trim().toUpperCase());
        } else {
            log.setStatus("REVIEWED");
        }
        if (body.containsKey("adminNotes")) {
            log.setAdminNotes(body.get("adminNotes"));
        }
        log.setReviewedBy(session.email);
        log.setReviewedAt(OffsetDateTime.now());

        accountDeletionLogRepository.save(log);

        recordAudit(session.email, session.role, "DELETION_REVIEW", id.toString(),
                "Status=" + log.getStatus() + ", Notes=" + log.getAdminNotes(), ip);

        return ResponseEntity.ok(log);
    }

    // =========================================================================
    // 9. MODULE 8: BROADCAST CAMPAIGNS & IMMUTABLE AUDIT LOGS (SUPER_ADMIN ONLY)
    // =========================================================================

    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Object>> sendBroadcast(
            @RequestBody BroadcastRequest req,
            HttpServletRequest request) {
        AdminSession session = requireAdmin(request, "SUPER_ADMIN");
        String ip = extractClientIp(request);

        if (req.getTitle() == null || req.getTitle().isBlank() || req.getBody() == null || req.getBody().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title and Body are required");
        }

        String audience = req.getAudience() != null ? req.getAudience().trim().toUpperCase() : "ALL_USERS";
        String targetCity = req.getCity() != null ? req.getCity().trim().toLowerCase() : "";

        List<User> users = userRepository.findAll();
        Map<UUID, Profile> profileMap = profileRepository.findAll().stream()
                .collect(Collectors.toMap(Profile::getUserId, p -> p, (a, b) -> a));

        List<UserNotification> notifications = new ArrayList<>();
        for (User u : users) {
            Profile p = profileMap.get(u.getId());
            String status = resolveVerificationStatus(u, p);

            if ("PENDING_SELFIE".equals(audience) && !"PENDING".equals(status)) continue;
            if ("UNVERIFIED_SELFIE".equals(audience) && "VERIFIED".equals(status)) continue;
            if ("CITY".equals(audience) && !targetCity.isEmpty()) {
                String c = p != null && p.getCity() != null ? p.getCity().toLowerCase() : "";
                String mc = p != null && p.getMicroCircle() != null ? p.getMicroCircle().toLowerCase() : "";
                if (!c.contains(targetCity) && !mc.contains(targetCity)) continue;
            }

            notifications.add(UserNotification.builder()
                    .userId(u.getId())
                    .type("SYSTEM")
                    .title(req.getTitle().trim())
                    .body(req.getBody().trim())
                    .isRead(false)
                    .build());
        }

        notificationRepository.saveAll(notifications);

        recordAudit(session.email, session.role, "BROADCAST_SENT", audience,
                "Sent '" + req.getTitle() + "' to " + notifications.size() + " users", ip);

        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "audience", audience,
                "recipientsCount", notifications.size()
        ));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AdminAuditEntry>> getAuditLogs(HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN");
        return ResponseEntity.ok(auditLogs);
    }

    // =========================================================================
    // 10. MODULE 9: MARKETING & OFFER EMAIL CAMPAIGN ENGINE (OPT-IN SUBSCRIBERS)
    // =========================================================================

    @GetMapping("/marketing/overview")
    public ResponseEntity<Map<String, Object>> getMarketingOverview(HttpServletRequest request) {
        requireAdmin(request, "SUPER_ADMIN");

        List<User> allUsers = userRepository.findAll();

        long totalWithEmail = 0;
        long totalOptedIn = 0;

        for (User u : allUsers) {
            boolean hasEmail = u.getEmail() != null && !u.getEmail().isBlank();
            boolean optedIn = Boolean.TRUE.equals(u.getMarketingOptIn());
            if (hasEmail) totalWithEmail++;
            if (hasEmail && optedIn) totalOptedIn++;
        }

        boolean smtpReady = smtpHost != null && !smtpHost.isBlank() && smtpUsername != null && !smtpUsername.isBlank();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalUsers", allUsers.size());
        res.put("totalWithEmail", totalWithEmail);
        res.put("totalOptedIn", totalOptedIn);
        res.put("smtpConfigured", smtpReady);
        res.put("smtpHost", smtpHost != null ? smtpHost : "");
        res.put("smtpPort", smtpPort);
        res.put("smtpUsername", smtpUsername != null ? smtpUsername : "");
        res.put("smtpFrom", smtpFrom != null ? smtpFrom : "offers@blunderr.in");
        res.put("campaigns", marketingEmailCampaignRepository.findTop30ByOrderByCreatedAtDesc());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/marketing/smtp-config")
    public ResponseEntity<Map<String, Object>> updateSmtpConfig(
            @RequestBody SmtpConfigRequest req,
            HttpServletRequest request) {
        AdminSession session = requireAdmin(request, "SUPER_ADMIN");
        String ip = extractClientIp(request);

        if (req.getHost() != null) this.smtpHost = req.getHost().trim();
        if (req.getPort() != null && req.getPort() > 0) this.smtpPort = req.getPort();
        if (req.getUsername() != null) this.smtpUsername = req.getUsername().trim();
        if (req.getPassword() != null && !req.getPassword().isBlank()) this.smtpPassword = req.getPassword().trim();
        if (req.getFromEmail() != null && !req.getFromEmail().isBlank()) this.smtpFrom = req.getFromEmail().trim();

        recordAudit(session.email, session.role, "SMTP_CONFIG_UPDATED", this.smtpHost,
                "Updated SMTP sender config (" + this.smtpFrom + ")", ip);

        return ResponseEntity.ok(Map.of(
                "status", "updated",
                "smtpConfigured", !this.smtpHost.isBlank() && !this.smtpUsername.isBlank(),
                "smtpHost", this.smtpHost,
                "smtpPort", this.smtpPort,
                "smtpFrom", this.smtpFrom
        ));
    }

    @PostMapping("/marketing/send-email")
    public ResponseEntity<Map<String, Object>> sendMarketingOfferEmail(
            @RequestBody MarketingEmailRequest req,
            HttpServletRequest request) {
        AdminSession session = requireAdmin(request, "SUPER_ADMIN");
        String ip = extractClientIp(request);

        if (req.getSubject() == null || req.getSubject().isBlank() || req.getBody() == null || req.getBody().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email Subject and Message Body are required");
        }

        String audience = req.getAudience() != null ? req.getAudience().trim().toUpperCase() : "OPTED_IN_ALL";
        String cityFilter = req.getCity() != null ? req.getCity().trim().toLowerCase() : "";

        List<User> allUsers = userRepository.findAll();
        Map<UUID, Profile> profileMap = profileRepository.findAll().stream()
                .collect(Collectors.toMap(Profile::getUserId, p -> p, (a, b) -> a));

        // Strictly filter ONLY users who have opted in for marketing/offer emails AND have a valid email address
        List<User> recipients = new ArrayList<>();
        for (User u : allUsers) {
            if (!Boolean.TRUE.equals(u.getMarketingOptIn())) continue;
            if (u.getEmail() == null || u.getEmail().isBlank() || !u.getEmail().contains("@")) continue;

            Profile p = profileMap.get(u.getId());
            if ("OPTED_IN_NON_VIP".equals(audience) && Boolean.TRUE.equals(u.getHasActivePass())) {
                continue;
            }
            if ("OPTED_IN_CITY".equals(audience) && !cityFilter.isEmpty()) {
                String c = p != null && p.getCity() != null ? p.getCity().toLowerCase() : "";
                String mc = p != null && p.getMicroCircle() != null ? p.getMicroCircle().toLowerCase() : "";
                if (!c.contains(cityFilter) && !mc.contains(cityFilter)) continue;
            }
            recipients.add(u);
        }

        boolean smtpReady = smtpHost != null && !smtpHost.isBlank() && smtpUsername != null && !smtpUsername.isBlank();
        JavaMailSenderImpl mailSender = null;
        if (smtpReady) {
            mailSender = new JavaMailSenderImpl();
            mailSender.setHost(smtpHost);
            mailSender.setPort(smtpPort);
            mailSender.setUsername(smtpUsername);
            mailSender.setPassword(smtpPassword);
            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.connectiontimeout", "6000");
            props.put("mail.smtp.timeout", "6000");
        }

        int smtpDelivered = 0;
        List<UserNotification> inAppOffers = new ArrayList<>();

        for (User u : recipients) {
            Profile p = profileMap.get(u.getId());
            String name = p != null && p.getDisplayName() != null && !p.getDisplayName().isBlank()
                    ? p.getDisplayName()
                    : "There";

            String htmlMail = buildBrandedOfferEmailHtml(
                    name,
                    u.getEmail(),
                    req.getSubject().trim(),
                    req.getOfferBadge(),
                    req.getCouponCode(),
                    req.getBody().trim(),
                    req.getCtaText(),
                    req.getCtaUrl()
            );

            if (mailSender != null) {
                try {
                    MimeMessage mime = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
                    helper.setFrom(smtpFrom != null && !smtpFrom.isBlank() ? smtpFrom : "offers@blunderr.in");
                    helper.setTo(u.getEmail().trim());
                    helper.setSubject(req.getSubject().trim());
                    helper.setText(htmlMail, true);
                    mailSender.send(mime);
                    smtpDelivered++;
                } catch (Exception e) {
                    log.warn("SMTP send failed for {}: {}", u.getEmail(), e.getMessage());
                }
            }

            String inAppBody = req.getBody().trim();
            if (req.getCouponCode() != null && !req.getCouponCode().isBlank()) {
                inAppBody += " | Use Coupon Code: " + req.getCouponCode().trim();
            }
            inAppOffers.add(UserNotification.builder()
                    .userId(u.getId())
                    .type("OFFER")
                    .title("🎁 " + req.getSubject().trim())
                    .body(inAppBody)
                    .isRead(false)
                    .build());
        }

        if (!inAppOffers.isEmpty()) {
            notificationRepository.saveAll(inAppOffers);
        }

        MarketingEmailCampaign savedCampaign = marketingEmailCampaignRepository.save(MarketingEmailCampaign.builder()
                .subject(req.getSubject().trim())
                .offerBadge(req.getOfferBadge() != null ? req.getOfferBadge().trim() : null)
                .couponCode(req.getCouponCode() != null ? req.getCouponCode().trim() : null)
                .ctaText(req.getCtaText() != null ? req.getCtaText().trim() : "Claim Offer in BlunderR")
                .ctaUrl(req.getCtaUrl() != null ? req.getCtaUrl().trim() : "https://blunderr.in/app/")
                .bodyText(req.getBody().trim())
                .audienceFilter(audience)
                .cityFilter(req.getCity())
                .recipientsCount(recipients.size())
                .smtpDeliveredCount(smtpDelivered)
                .inAppDeliveredCount(inAppOffers.size())
                .sentByEmail(session.email)
                .build());

        recordAudit(session.email, session.role, "MARKETING_EMAIL_SENT", savedCampaign.getId().toString(),
                String.format("Subject='%s' | Opted-In Recipients=%d | SMTP=%d | InApp=%d",
                        savedCampaign.getSubject(), recipients.size(), smtpDelivered, inAppOffers.size()), ip);

        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "campaignId", savedCampaign.getId(),
                "recipientsCount", recipients.size(),
                "smtpDeliveredCount", smtpDelivered,
                "inAppDeliveredCount", inAppOffers.size(),
                "smtpConfigured", smtpReady
        ));
    }

    @GetMapping(value = "/marketing/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribeFromMarketing(@RequestParam String email) {
        String clean = email != null ? email.trim().toLowerCase() : "";
        int updated = 0;
        if (!clean.isEmpty()) {
            for (User u : userRepository.findAll()) {
                if (u.getEmail() != null && u.getEmail().trim().equalsIgnoreCase(clean)) {
                    u.setMarketingOptIn(false);
                    userRepository.save(u);
                    updated++;
                }
            }
        }
        String html = """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"><title>Unsubscribed • BlunderR</title></head>
                <body style="background:#05060A;color:#F8FAFC;font-family:-apple-system,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;">
                  <div style="background:#111422;border:1px solid rgba(255,255,255,0.15);border-radius:20px;padding:32px;max-width:420px;text-align:center;">
                    <div style="font-size:32px;margin-bottom:10px;">✅</div>
                    <h2 style="margin:0 0 8px;">You Have Been Unsubscribed</h2>
                    <p style="color:#94A3B8;font-size:14px;line-height:1.5;">
                      <b>%s</b> has been removed from BlunderR promotional & offer emails.
                    </p>
                    <a href="https://blunderr.in" style="display:inline-block;margin-top:16px;color:#FF385C;text-decoration:none;font-weight:700;">← Return to BlunderR.in</a>
                  </div>
                </body></html>
                """.formatted(clean.replace("<", "&lt;"));
        return ResponseEntity.ok(html);
    }

    private String buildBrandedOfferEmailHtml(
            String name,
            String recipientEmail,
            String subject,
            String offerBadge,
            String couponCode,
            String bodyText,
            String ctaText,
            String ctaUrl) {
        String safeBody = bodyText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br/>");
        String badgeHtml = (offerBadge != null && !offerBadge.isBlank())
                ? "<div style=\"display:inline-block;background:rgba(255,56,92,0.18);border:1px solid #FF385C;color:#FDA4AF;font-size:11px;font-weight:800;padding:5px 12px;border-radius:99px;margin-bottom:14px;letter-spacing:0.6px;text-transform:uppercase;\">🔥 " + offerBadge.trim() + "</div>"
                : "";
        String couponHtml = (couponCode != null && !couponCode.isBlank())
                ? "<div style=\"margin:20px 0;padding:16px;background:#070912;border:1px dashed #F59E0B;border-radius:14px;text-align:center;\">" +
                  "<div style=\"font-size:10px;color:#94A3B8;letter-spacing:1px;text-transform:uppercase;font-weight:700;\">USE EXCLUSIVE COUPON CODE</div>" +
                  "<div style=\"font-size:22px;font-weight:900;color:#FCD34D;letter-spacing:2px;margin-top:4px;font-family:monospace;\">" + couponCode.trim() + "</div>" +
                  "</div>"
                : "";
        String btnLabel = (ctaText != null && !ctaText.isBlank()) ? ctaText.trim() : "Claim Offer on BlunderR ➔";
        String btnHref = (ctaUrl != null && !ctaUrl.isBlank()) ? ctaUrl.trim() : "https://blunderr.in/app/";
        String unsubUrl = "https://admin.blunderr.in/v1/admin/marketing/unsubscribe?email=" +
                java.net.URLEncoder.encode(recipientEmail, StandardCharsets.UTF_8);

        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:24px;background-color:#05060A;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;color:#F8FAFC;">
                  <div style="max-width:560px;margin:0 auto;background:#111422;border:1px solid rgba(255,255,255,0.14);border-radius:24px;overflow:hidden;">
                    <div style="background:linear-gradient(135deg,#FF385C,#7C3AED);padding:22px 28px;display:flex;align-items:center;">
                      <div style="font-size:22px;font-weight:900;color:#FFFFFF;letter-spacing:-0.3px;">BlunderR • Exclusive Member Offer</div>
                    </div>
                    <div style="padding:28px;">
                      %s
                      <h2 style="margin:0 0 12px;font-size:22px;color:#FFFFFF;">%s</h2>
                      <p style="font-size:14px;color:#CBD5E1;margin:0 0 14px;">Hi <b>%s</b>,</p>
                      <div style="font-size:14px;line-height:1.65;color:#E2E8F0;">%s</div>
                      %s
                      <div style="margin-top:24px;text-align:center;">
                        <a href="%s" style="display:inline-block;background:linear-gradient(135deg,#FF385C,#E11D48);color:#FFFFFF;text-decoration:none;font-weight:800;font-size:14px;padding:13px 26px;border-radius:12px;">%s</a>
                      </div>
                    </div>
                    <div style="padding:16px 28px;background:#0B0D16;border-top:1px solid rgba(255,255,255,0.08);font-size:11px;color:#64748B;text-align:center;line-height:1.5;">
                      You are receiving this email because you opted in to receive exclusive offers & marketing updates from BlunderR.<br/>
                      <a href="%s" style="color:#94A3B8;text-decoration:underline;">Unsubscribe with 1 click</a> • <a href="https://blunderr.in" style="color:#94A3B8;text-decoration:none;">BlunderR.in</a>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(badgeHtml, subject, name, safeBody, couponHtml, btnHref, btnLabel, unsubUrl);
    }

    // =========================================================================
    // HELPER & CRYPTOGRAPHIC TOKEN METHODS
    // =========================================================================

    private Map<String, Object> buildCompactUserRow(User u, Profile p, boolean maskPhone) {
        Map<String, Object> row = new LinkedHashMap<>();
        List<String> photos = p != null ? extractProfilePhotos(p) : List.of();
        row.put("userId", u.getId());
        row.put("displayName", (p != null && p.getDisplayName() != null && !p.getDisplayName().isBlank()) ? p.getDisplayName().trim() : "Unspecified");
        row.put("phoneMasked", maskPhone ? maskPhone(u.getPhoneE164()) : u.getPhoneE164());
        row.put("email", u.getEmail() != null ? u.getEmail() : "");
        row.put("marketingOptIn", Boolean.TRUE.equals(u.getMarketingOptIn()));
        row.put("gender", u.getGender() != null ? u.getGender().name() : (p != null && p.getGenderDisplay() != null ? p.getGenderDisplay() : "UNSPECIFIED"));
        row.put("city", (p != null && p.getCity() != null && !p.getCity().isBlank()) ? p.getCity().trim() : "Not specified");
        row.put("microCircle", (p != null && p.getMicroCircle() != null && !p.getMicroCircle().isBlank()) ? p.getMicroCircle().trim() : "Unassigned");
        row.put("verificationStatus", resolveVerificationStatus(u, p));
        row.put("faceVerified", Boolean.TRUE.equals(u.getFaceVerified()));
        row.put("digilockerVerified", Boolean.TRUE.equals(u.getDigilockerVerified()));
        row.put("livenessScore", u.getLivenessScore() != null ? u.getLivenessScore() : 0.0);
        row.put("karmaScore", u.getKarmaScore() != null ? u.getKarmaScore() : 100);
        row.put("isShadowbanned", Boolean.TRUE.equals(u.getIsIncognito()));
        row.put("hasActivePass", Boolean.TRUE.equals(u.getHasActivePass()));
        row.put("passExpiry", u.getPassExpiry() != null ? u.getPassExpiry().toString() : null);
        row.put("sparksBalance", u.getSparksBalance() != null ? u.getSparksBalance() : 0);
        row.put("boostsBalance", u.getBoostsBalance() != null ? u.getBoostsBalance() : 0);
        row.put("directDmsBalance", u.getDirectDmsBalance() != null ? u.getDirectDmsBalance() : 0);
        row.put("selfieUrl", p != null ? p.getSelfieUrl() : null);
        row.put("primaryPhoto", !photos.isEmpty() ? photos.get(0) : (p != null ? p.getSelfieUrl() : null));
        row.put("photos", photos);
        row.put("photoCount", photos.size());
        row.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
        return row;
    }

    private String resolveVerificationStatus(User u, Profile p) {
        if (p != null && p.getVerificationStatus() != null && !p.getVerificationStatus().isBlank()) {
            return p.getVerificationStatus().toUpperCase();
        }
        if (Boolean.TRUE.equals(u.getFaceVerified())) {
            return "VERIFIED";
        }
        if (p != null && p.getSelfieUrl() != null && !p.getSelfieUrl().isBlank()) {
            return "PENDING";
        }
        return "UNVERIFIED";
    }

    private List<String> extractSlotOnlyPhotos(Profile p) {
        List<String> list = new ArrayList<>();
        if (p == null) return list;
        for (String s : Arrays.asList(p.getPhoto1(), p.getPhoto2(), p.getPhoto3(), p.getPhoto4(), p.getPhoto5(), p.getPhoto6())) {
            if (s != null && !s.isBlank() && !list.contains(s)) {
                list.add(s);
            }
        }
        return list;
    }

    private List<String> extractProfilePhotos(Profile p) {
        List<String> combined = extractSlotOnlyPhotos(p);
        if (p != null && p.getPhotosJson() != null && !p.getPhotosJson().isBlank()) {
            try {
                List<String> jsonList = objectMapper.readValue(p.getPhotosJson(), new TypeReference<List<String>>() {});
                for (String url : jsonList) {
                    if (url != null && !url.isBlank() && !combined.contains(url)) {
                        combined.add(url);
                    }
                }
            } catch (Exception ignored) {}
        }
        return combined;
    }

    private String extractPrimaryPhoto(Profile p) {
        if (p == null) return null;
        if (p.getSelfieUrl() != null && !p.getSelfieUrl().isBlank()) return p.getSelfieUrl();
        List<String> photos = extractProfilePhotos(p);
        return (!photos.isEmpty() && photos.get(0) != null) ? photos.get(0) : null;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return "+91 ******";
        return phone.substring(0, Math.min(3, phone.length())) + " ******" + phone.substring(phone.length() - 4);
    }

    private record AdminSession(String email, String role, long expiresAt) {}

    private AdminSession requireAdmin(HttpServletRequest request, String... allowedRoles) {
        String token = request.getHeader("X-Admin-Token");
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-Admin-Token header");
        }
        AdminSession session = verifyAdminToken(token);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired Admin Token");
        }
        if (allowedRoles != null && allowedRoles.length > 0) {
            boolean allowed = Arrays.asList(allowedRoles).contains(session.role);
            if (!allowed) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient admin role permissions");
            }
        }
        return session;
    }

    private String generateAdminToken(String email, String role, long expiresAt) {
        String payload = email + "|" + role + "|" + expiresAt;
        String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String sig = hmacSha256(payloadB64);
        return payloadB64 + "." + sig;
    }

    private AdminSession verifyAdminToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) return null;
            String expectedSig = hmacSha256(parts[0]);
            if (! expectedSig.equals(parts[1])) return null;
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\|");
            if (fields.length != 3) return null;
            long expiresAt = Long.parseLong(fields[2]);
            if (System.currentTimeMillis() > expiresAt) return null;
            return new AdminSession(fields[0], fields[1], expiresAt);
        } catch (Exception e) {
            return null;
        }
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new RuntimeException("HMAC signing error", e);
        }
    }

    private void checkRateLimit(String ip) {
        long now = System.currentTimeMillis();
        List<Long> attempts = failedLoginTracker.getOrDefault(ip, new ArrayList<>());
        attempts.removeIf(ts -> (now - ts) > 60_000L);
        if (attempts.size() >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed login attempts from IP " + ip + ". Please wait 60 seconds.");
        }
    }

    private void recordFailedLogin(String ip) {
        long now = System.currentTimeMillis();
        failedLoginTracker.compute(ip, (k, list) -> {
            List<Long> l = list != null ? list : new CopyOnWriteArrayList<>();
            l.removeIf(ts -> (now - ts) > 60_000L);
            l.add(now);
            return l;
        });
    }

    private void recordAudit(String email, String role, String action, String targetId, String details, String ip) {
        AdminAuditEntry entry = AdminAuditEntry.builder()
                .id("AUD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .adminEmail(email)
                .adminRole(role)
                .action(action)
                .targetId(targetId)
                .details(details)
                .ipAddress(ip)
                .timestamp(OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
        auditLogs.add(0, entry);
        if (auditLogs.size() > 500) {
            auditLogs.remove(auditLogs.size() - 1);
        }
        log.info("[ADMIN_AUDIT] admin={} role={} action={} target={} ip={} details={}",
                email, role, action, targetId, ip, details);
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return "UNKNOWN";
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "UNKNOWN";
    }
}
