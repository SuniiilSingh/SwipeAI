package com.match.SwipeAI.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.SwipeAI.dto.ProfileDto;
import com.match.SwipeAI.model.Profile;
import com.match.SwipeAI.model.User;
import com.match.SwipeAI.repository.*;
import com.match.SwipeAI.service.engine.CosmicChemistryEngine;
import com.match.SwipeAI.service.integration.FaceMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.match.SwipeAI.enums.ActionType;
import com.match.SwipeAI.enums.MatchStatus;
import com.match.SwipeAI.enums.TicketCategory;
import com.match.SwipeAI.enums.TicketStatus;
import com.match.SwipeAI.model.AccountDeletionLog;
import com.match.SwipeAI.model.Interaction;
import com.match.SwipeAI.model.Match;
import com.match.SwipeAI.model.SupportTicket;
import com.match.SwipeAI.repository.AccountDeletionLogRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final DesireProfileRepository desireProfileRepository;
    private final PushTokenRepository pushTokenRepository;
    private final UserContactShieldRepository userContactShieldRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final InteractionRepository interactionRepository;
    private final MatchRepository matchRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final UserReportRepository userReportRepository;
    private final UserBlockRepository userBlockRepository;
    private final com.match.SwipeAI.service.engine.MatchKarmaService karmaService;
    private final CosmicChemistryEngine cosmicChemistryEngine;
    private final com.match.SwipeAI.service.integration.FaceMatchService faceMatchService;
    private final AccountDeletionLogRepository accountDeletionLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProfileDto.ProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        Profile profile = profileRepository.findById(userId)
                .orElseGet(() -> Profile.builder()
                        .userId(user.getId())
                        .displayName("User")
                        .languagesSpoken(List.of("English", "Hindi"))
                        .photosJson("[]")
                        .build());

        return mapToResponse(user, profile);
    }

    @Transactional
    public ProfileDto.ProfileResponse updateProfile(UUID userId, ProfileDto.ProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (request.getIntent() != null) user.setIntent(request.getIntent());
        if (request.getGender() != null) user.setGender(request.getGender());
        if (request.getBirthDate() != null) user.setBirthDate(request.getBirthDate());
        if (request.getLatitude() != null) user.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) user.setLongitude(request.getLongitude());
        if (request.getEmail() != null) {
            String cleanEmail = sanitize(request.getEmail().toLowerCase(), 150);
            user.setEmail(cleanEmail != null && cleanEmail.isBlank() ? null : cleanEmail);
        }
        if (request.getMarketingOptIn() != null) {
            user.setMarketingOptIn(request.getMarketingOptIn());
            if (Boolean.TRUE.equals(request.getMarketingOptIn())) {
                user.setMarketingOptInAt(OffsetDateTime.now());
            }
        }
        userRepository.save(user);

        Profile profile = profileRepository.findById(userId)
                .orElseGet(() -> Profile.builder().userId(user.getId()).build());

        if (request.getDisplayName() != null) profile.setDisplayName(sanitize(request.getDisplayName(), 30));
        if (request.getFullName() != null) profile.setDisplayName(sanitize(request.getFullName(), 30));
        if (request.getBio() != null) profile.setBio(sanitize(request.getBio(), 500));
        if (request.getDietaryPref() != null) profile.setDietaryPref(request.getDietaryPref());
        if (request.getLivingStatus() != null) profile.setLivingStatus(request.getLivingStatus());
        if (request.getLanguagesSpoken() != null) profile.setLanguagesSpoken(request.getLanguagesSpoken());
        if (request.getZodiacSign() != null) profile.setZodiacSign(sanitize(request.getZodiacSign(), 30));
        if (request.getSunSign() != null) profile.setSunSign(sanitize(request.getSunSign(), 30));
        if (request.getMoonSign() != null) profile.setMoonSign(sanitize(request.getMoonSign(), 30));
        if (request.getCompany() != null) profile.setCompany(sanitize(request.getCompany(), 30));
        if (request.getOccupation() != null) profile.setOccupation(sanitize(request.getOccupation(), 100));
        if (request.getJob() != null) profile.setJob(sanitize(request.getJob(), 100));
        if (request.getEducation() != null) profile.setEducation(sanitize(request.getEducation(), 100));
        if (request.getInstitute() != null) profile.setInstitute(sanitize(request.getInstitute(), 30));
        if (request.getInterests() != null) profile.setInterests(sanitize(request.getInterests(), 250));
        if (request.getHeight() != null) profile.setHeight(Math.min(250, Math.max(100, request.getHeight())));
        if (request.getLocation() != null) profile.setLocation(sanitize(request.getLocation(), 25));
        if (request.getMaxDistanceKm() != null) profile.setMaxDistanceKm(Math.min(500, Math.max(1, request.getMaxDistanceKm())));
        if (request.getSexualOrientation() != null) profile.setSexualOrientation(sanitize(request.getSexualOrientation(), 50));
        if (request.getShowOrientationOnProfile() != null) profile.setShowOrientationOnProfile(request.getShowOrientationOnProfile());
        if (request.getGenderDisplay() != null) profile.setGenderDisplay(sanitize(request.getGenderDisplay(), 50));
        if (request.getShowGenderOnProfile() != null) profile.setShowGenderOnProfile(request.getShowGenderOnProfile());
        if (request.getGenderPreferenceDisplay() != null) profile.setGenderPreferenceDisplay(sanitize(request.getGenderPreferenceDisplay(), 50));
        if (request.getRelationshipIntent() != null) profile.setRelationshipIntent(sanitize(request.getRelationshipIntent(), 50));
        if (request.getProfilePromptQuestion() != null) profile.setProfilePromptQuestion(sanitize(request.getProfilePromptQuestion(), 150));
        if (request.getProfilePromptAnswer() != null) profile.setProfilePromptAnswer(sanitize(request.getProfilePromptAnswer(), 500));

        boolean photosOrSelfieUpdated = false;
        boolean anySlotExplicitlyUpdated =
                request.getPhoto1() != null ||
                request.getPhoto2() != null ||
                request.getPhoto3() != null ||
                request.getPhoto4() != null ||
                request.getPhoto5() != null ||
                request.getPhoto6() != null;

        if (request.getPhoto1() != null) {
            String newPhoto1 = sanitizePhotoUrl(request.getPhoto1());
            if (profile.getPhoto1() != null && !profile.getPhoto1().equals(newPhoto1)) {
                if (Boolean.TRUE.equals(user.getFaceVerified())) {
                    log.info("User {} modified primary profile photo; resetting biometric face verification to PENDING.", userId);
                    user.setFaceVerified(false);
                    userRepository.save(user);
                }
            }
            profile.setPhoto1(newPhoto1);
            photosOrSelfieUpdated = true;
        }
        if (request.getPhoto2() != null) { profile.setPhoto2(sanitizePhotoUrl(request.getPhoto2())); photosOrSelfieUpdated = true; }
        if (request.getPhoto3() != null) { profile.setPhoto3(sanitizePhotoUrl(request.getPhoto3())); photosOrSelfieUpdated = true; }
        if (request.getPhoto4() != null) { profile.setPhoto4(sanitizePhotoUrl(request.getPhoto4())); photosOrSelfieUpdated = true; }
        if (request.getPhoto5() != null) { profile.setPhoto5(sanitizePhotoUrl(request.getPhoto5())); photosOrSelfieUpdated = true; }
        if (request.getPhoto6() != null) { profile.setPhoto6(sanitizePhotoUrl(request.getPhoto6())); photosOrSelfieUpdated = true; }

        if (request.getSelfieUrl() != null) {
            String cleanSelfie = sanitizePhotoUrl(request.getSelfieUrl());
            if (cleanSelfie != null && !cleanSelfie.isBlank()) {
                profile.setSelfieUrl(cleanSelfie);
                profile.setVerificationStatus("PENDING");
                user.setFaceVerified(false);
                userRepository.save(user);
                photosOrSelfieUpdated = true;
            }
        }
        if (request.getVerificationStatus() != null && !request.getVerificationStatus().isBlank()) {
            profile.setVerificationStatus(sanitize(request.getVerificationStatus().toUpperCase(), 30));
        }

        if (request.getSmokingHabit() != null) profile.setSmokingHabit(sanitize(request.getSmokingHabit(), 50));
        if (request.getDrinkingHabit() != null) profile.setDrinkingHabit(sanitize(request.getDrinkingHabit(), 50));
        if (request.getHobbies() != null) profile.setHobbies(sanitize(request.getHobbies(), 250));
        if (request.getVacationPreference() != null) profile.setVacationPreference(sanitize(request.getVacationPreference(), 50));
        if (request.getCity() != null) profile.setCity(sanitize(request.getCity(), 25));
        if (request.getNeighborhood() != null) profile.setNeighborhood(sanitize(request.getNeighborhood(), 50));
        if (request.getMicroCircles() != null) {
            List<String> validCircles = request.getMicroCircles().stream()
                    .filter(s -> s != null && !s.trim().isEmpty())
                    .map(s -> sanitize(s.trim(), 100))
                    .distinct()
                    .limit(5)
                    .toList();
            try {
                profile.setMicroCirclesJson(objectMapper.writeValueAsString(validCircles));
            } catch (Exception ignored) {}
            if (!validCircles.isEmpty()) {
                profile.setMicroCircle(validCircles.get(0));
            } else {
                profile.setMicroCircle(null);
            }
        } else if (request.getMicroCircle() != null) {
            profile.setMicroCircle(sanitize(request.getMicroCircle(), 100));
            if (profile.getMicroCirclesJson() == null || profile.getMicroCirclesJson().isBlank()) {
                try {
                    profile.setMicroCirclesJson(objectMapper.writeValueAsString(List.of(profile.getMicroCircle())));
                } catch (Exception ignored) {}
            }
        }
        if (request.getVoicePromptUrl() != null) profile.setVoicePromptUrl(request.getVoicePromptUrl().trim().isEmpty() ? null : request.getVoicePromptUrl().trim());
        if (request.getVoicePromptDuration() != null) profile.setVoicePromptDuration(request.getVoicePromptDuration());
        if (request.getVoicePromptText() != null) profile.setVoicePromptText(sanitize(request.getVoicePromptText(), 300));
        if (request.getSelectedMemeUrl() != null) profile.setSelectedMemeUrl(request.getSelectedMemeUrl().trim().isEmpty() ? null : request.getSelectedMemeUrl().trim());
        if (request.getSelectedMemeTitle() != null) profile.setSelectedMemeTitle(sanitize(request.getSelectedMemeTitle(), 150));

        if (request.getPhotos() != null && !anySlotExplicitlyUpdated) {
            try {
                List<String> cleanPhotos = request.getPhotos().stream()
                        .map(this::sanitizePhotoUrl)
                        .filter(p -> p != null && !p.trim().isEmpty())
                        .toList();
                profile.setPhoto1(cleanPhotos.size() > 0 ? cleanPhotos.get(0) : null);
                profile.setPhoto2(cleanPhotos.size() > 1 ? cleanPhotos.get(1) : null);
                profile.setPhoto3(cleanPhotos.size() > 2 ? cleanPhotos.get(2) : null);
                profile.setPhoto4(cleanPhotos.size() > 3 ? cleanPhotos.get(3) : null);
                profile.setPhoto5(cleanPhotos.size() > 4 ? cleanPhotos.get(4) : null);
                profile.setPhoto6(cleanPhotos.size() > 5 ? cleanPhotos.get(5) : null);
                photosOrSelfieUpdated = true;
            } catch (Exception ignored) {}
        }

        // Always synchronize photosJson from all non-blank photo1..photo6 slots (plus any extra items in request.getPhotos())
        List<String> syncPhotos = new ArrayList<>();
        if (profile.getPhoto1() != null && !profile.getPhoto1().isBlank()) syncPhotos.add(profile.getPhoto1());
        if (profile.getPhoto2() != null && !profile.getPhoto2().isBlank()) syncPhotos.add(profile.getPhoto2());
        if (profile.getPhoto3() != null && !profile.getPhoto3().isBlank()) syncPhotos.add(profile.getPhoto3());
        if (profile.getPhoto4() != null && !profile.getPhoto4().isBlank()) syncPhotos.add(profile.getPhoto4());
        if (profile.getPhoto5() != null && !profile.getPhoto5().isBlank()) syncPhotos.add(profile.getPhoto5());
        if (profile.getPhoto6() != null && !profile.getPhoto6().isBlank()) syncPhotos.add(profile.getPhoto6());
        if (request.getPhotos() != null) {
            for (String raw : request.getPhotos()) {
                String clean = sanitizePhotoUrl(raw);
                if (clean != null && !clean.isBlank() && !syncPhotos.contains(clean) && syncPhotos.size() < 6) {
                    syncPhotos.add(clean);
                }
            }
        }
        try {
            profile.setPhotosJson(objectMapper.writeValueAsString(syncPhotos));
        } catch (Exception ignored) {}

        if (photosOrSelfieUpdated && profile.getSelfieUrl() != null && !profile.getSelfieUrl().isBlank()) {
            profile.setVerificationStatus("PENDING");
        }

        profile = profileRepository.save(profile);

        if (photosOrSelfieUpdated && profile.getSelfieUrl() != null && !profile.getSelfieUrl().isBlank()) {
            triggerAsyncSelfieVerification(userId);
        }

        return mapToResponse(user, profile);
    }

    @Transactional
    public ProfileDto.ProfileResponse updateLocation(UUID userId, Double latitude, Double longitude) {
        return updateLocation(userId, latitude, longitude, null, null);
    }

    @Transactional
    public ProfileDto.ProfileResponse updateLocation(UUID userId, Double latitude, Double longitude, String city, String location) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (latitude != null && longitude != null) {
            user.setLatitude(latitude);
            user.setLongitude(longitude);
            userRepository.save(user);
        }

        Profile profile = profileRepository.findById(userId)
                .orElseGet(() -> Profile.builder().userId(user.getId()).build());

        boolean profileChanged = false;
        if (city != null && !city.isBlank()) {
            profile.setCity(city);
            profileChanged = true;
        }
        if (location != null && !location.isBlank()) {
            profile.setLocation(location);
            profileChanged = true;
        } else if (city != null && !city.isBlank() && (profile.getLocation() == null || profile.getLocation().isBlank())) {
            profile.setLocation(city);
            profileChanged = true;
        }

        if (profileChanged || profile.getUserId() == null) {
            profile.setUserId(user.getId());
            profile = profileRepository.save(profile);
        }

        return mapToResponse(user, profile);
    }

    @Transactional
    public ProfileDto.ProfileResponse updateVoicePrompt(UUID userId, ProfileDto.VoicePromptUploadRequest request) {
        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + userId));

        profile.setVoicePromptUrl(request.getVoicePromptUrl());
        profile.setVoicePromptDuration(request.getDurationSec());
        profile.setVoicePromptText(request.getPromptText());

        profileRepository.save(profile);
        return getProfile(userId);
    }

    @Transactional
    public void recordMemeSwipe(UUID userId, ProfileDto.MemeSwipeRequest request) {
        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + userId));

        List<Map<String, Object>> swipes = new ArrayList<>();
        if (profile.getMemeSwipesJson() != null && !profile.getMemeSwipesJson().isBlank()) {
            try {
                swipes = objectMapper.readValue(profile.getMemeSwipesJson(), new TypeReference<>() {});
            } catch (Exception ignored) {}
        }

        swipes.add(Map.of("memeId", request.getMemeId(), "liked", request.isLiked(), "timestamp", System.currentTimeMillis()));
        try {
            profile.setMemeSwipesJson(objectMapper.writeValueAsString(swipes));
            profileRepository.save(profile);
        } catch (Exception e) {
            log.error("Failed to save meme swipes", e);
        }
    }

    @Transactional
    public void deleteProfileWithAudit(UUID userId, String source, String reasonCategory, String remarks, String clientIp, String userAgent) {
        log.info("Recording churn audit & deleting account for userId: {}, source: {}, reason: {}", userId, source, reasonCategory);
        try {
            User user = userRepository.findById(userId).orElse(null);
            Profile profile = profileRepository.findById(userId).orElse(null);
            if (user != null) {
                String phone = user.getPhoneE164();
                String masked = maskPhoneHelper(phone);
                AccountDeletionLog deletionLog = AccountDeletionLog.builder()
                        .userId(userId)
                        .phoneE164(phone != null ? phone : "UNKNOWN")
                        .phoneMasked(masked)
                        .displayName(profile != null && profile.getDisplayName() != null ? profile.getDisplayName().trim() : "Unknown")
                        .email(user.getEmail())
                        .city(profile != null ? profile.getCity() : null)
                        .source(source != null ? source : "IN_APP_SETTINGS")
                        .reasonCategory(reasonCategory != null && !reasonCategory.isBlank() ? reasonCategory : "OTHER")
                        .remarks(remarks)
                        .clientIp(clientIp)
                        .userAgent(userAgent)
                        .status("COMPLETED")
                        .build();
                accountDeletionLogRepository.save(deletionLog);
            }
        } catch (Exception e) {
            log.error("Failed to save AccountDeletionLog for userId {}: {}", userId, e.getMessage());
        }
        deleteProfile(userId);
    }

    private String maskPhoneHelper(String p) {
        if (p == null || p.length() < 7) return p;
        int len = p.length();
        return p.substring(0, 3) + " " + p.substring(3, Math.min(5, len)) + "*****" + p.substring(Math.max(0, len - 3));
    }

    @Transactional
    public void deleteProfile(UUID userId) {
        log.info("Deleting user profile and account for userId: {}", userId);
        try {
            desireProfileRepository.deleteById(userId);
        } catch (Exception e) {
            log.warn("Could not delete desire profile for userId {}: {}", userId, e.getMessage());
        }
        try {
            pushTokenRepository.deleteByUserId(userId);
        } catch (Exception e) {
            log.warn("Could not delete push tokens for userId {}: {}", userId, e.getMessage());
        }
        try {
            userContactShieldRepository.deleteByUserId(userId);
        } catch (Exception e) {
            log.warn("Could not delete contact shield for userId {}: {}", userId, e.getMessage());
        }
        try {
            userNotificationRepository.deleteByUserId(userId);
        } catch (Exception e) {
            log.warn("Could not delete notifications for userId {}: {}", userId, e.getMessage());
        }
        profileRepository.deleteById(userId);
        userRepository.deleteById(userId);
    }

    /**
     * Report a candidate profile for objectionable content, harassment, or fake identity.
     * Complies with Apple Review Guideline 1.2 & Google Play UGC Safety Policy.
     */
    @Transactional
    public void reportProfile(UUID reporterId, UUID targetUserId, String reason) {
        reportProfile(reporterId, targetUserId, reason, null);
    }

    @Transactional
    public void reportProfile(UUID reporterId, UUID targetUserId, String reason, String remarks) {
        log.warn("User {} reported candidate {} for reason: {}, remarks: {}", reporterId, targetUserId, reason, remarks);

        // 1. Penalize karma of reported user
        try {
            karmaService.penaltyHarassment(targetUserId);
        } catch (Exception e) {
            log.warn("Could not apply karma penalty: {}", e.getMessage());
        }

        // 2. Immediately mark as PASS so reporter never sees target again in discovery
        try {
            Interaction existing = interactionRepository.findByActorIdAndTargetId(reporterId, targetUserId).orElse(null);
            if (existing == null) {
                Interaction interaction = Interaction.builder()
                        .actorId(reporterId)
                        .targetId(targetUserId)
                        .actionType(ActionType.PASS)
                        .createdAt(OffsetDateTime.now())
                        .build();
                interactionRepository.save(interaction);
            } else {
                existing.setActionType(ActionType.PASS);
                interactionRepository.save(existing);
            }
        } catch (Exception e) {
            log.warn("Could not record PASS interaction for reported user: {}", e.getMessage());
        }

        // 3. Sever any existing match
        try {
            matchRepository.findMatchBetween(reporterId, targetUserId).ifPresent(m -> {
                m.setStatus(MatchStatus.UNMATCHED);
                matchRepository.save(m);
            });
        } catch (Exception e) {
            log.warn("Could not unmatch reported user: {}", e.getMessage());
        }

        // 4. Record UserReport & UserBlock for Admin Review Desk and in-app Unblock management
        try {
            com.match.SwipeAI.model.UserReport report = com.match.SwipeAI.model.UserReport.builder()
                    .reporterId(reporterId)
                    .reportedUserId(targetUserId)
                    .reason(reason != null ? reason : "Objectionable Content")
                    .remarks(remarks != null ? remarks.trim() : null)
                    .status("PENDING")
                    .build();
            userReportRepository.save(report);

            if (!userBlockRepository.existsByBlockerIdAndBlockedUserId(reporterId, targetUserId)) {
                com.match.SwipeAI.model.UserBlock block = com.match.SwipeAI.model.UserBlock.builder()
                        .blockerId(reporterId)
                        .blockedUserId(targetUserId)
                        .reason(reason != null ? reason : "Objectionable Content")
                        .remarks(remarks != null ? remarks.trim() : null)
                        .build();
                userBlockRepository.save(block);
            }
        } catch (Exception e) {
            log.warn("Could not save UserReport/UserBlock record: {}", e.getMessage());
        }

        // 5. Create Support / Trust & Safety moderation ticket
        try {
            Profile reporterProfile = profileRepository.findById(reporterId).orElse(null);
            Profile targetProfile = profileRepository.findById(targetUserId).orElse(null);
            String reporterName = reporterProfile != null && reporterProfile.getDisplayName() != null ? reporterProfile.getDisplayName().trim() : "User " + reporterId;
            String targetName = targetProfile != null && targetProfile.getDisplayName() != null ? targetProfile.getDisplayName().trim() : "User " + targetUserId;

            StringBuilder desc = new StringBuilder();
            desc.append("ACTION: REPORT_PROFILE\n");
            desc.append("Reporter: ").append(reporterName).append(" (ID: ").append(reporterId).append(")\n");
            desc.append("Reported Candidate: ").append(targetName).append(" (ID: ").append(targetUserId).append(")\n");
            desc.append("Reason Category: ").append(reason != null ? reason : "Objectionable Content").append("\n");
            if (remarks != null && !remarks.isBlank()) {
                desc.append("\n--- User Remarks for Admin Review ---\n");
                desc.append(remarks.trim()).append("\n");
            }

            String ticketNumber = "UGC-" + (10000 + new Random().nextInt(90000));
            SupportTicket ticket = SupportTicket.builder()
                    .userId(reporterId)
                    .ticketNumber(ticketNumber)
                    .category(TicketCategory.SAFETY_HARASSMENT)
                    .status(TicketStatus.PENDING)
                    .subject("Profile Report: " + targetName + " (" + (reason != null ? reason : "Objectionable Content") + ")")
                    .description(desc.toString())
                    .build();
            supportTicketRepository.save(ticket);
        } catch (Exception e) {
            log.warn("Could not create support ticket for report: {}", e.getMessage());
        }
    }

    /**
     * Block a user completely. Immediately hides them from discovery and severs any match.
     */
    @Transactional
    public void blockProfile(UUID blockerId, UUID targetUserId) {
        blockProfile(blockerId, targetUserId, "Direct Block", null);
    }

    @Transactional
    public void blockProfile(UUID blockerId, UUID targetUserId, String reason, String remarks) {
        log.info("User {} blocked target {}, reason: {}, remarks: {}", blockerId, targetUserId, reason, remarks);

        // 1. Record PASS interaction so blocker never sees target in discovery
        try {
            Interaction existing = interactionRepository.findByActorIdAndTargetId(blockerId, targetUserId).orElse(null);
            if (existing == null) {
                Interaction interaction = Interaction.builder()
                        .actorId(blockerId)
                        .targetId(targetUserId)
                        .actionType(ActionType.PASS)
                        .createdAt(OffsetDateTime.now())
                        .build();
                interactionRepository.save(interaction);
            } else {
                existing.setActionType(ActionType.PASS);
                interactionRepository.save(existing);
            }
        } catch (Exception e) {
            log.warn("Could not record block interaction: {}", e.getMessage());
        }

        // 2. Sever any existing match
        try {
            matchRepository.findMatchBetween(blockerId, targetUserId).ifPresent(m -> {
                m.setStatus(MatchStatus.UNMATCHED);
                matchRepository.save(m);
            });
        } catch (Exception e) {
            log.warn("Could not unmatch blocked user: {}", e.getMessage());
        }

        // 3. Record UserBlock entity for in-app unblock & review
        try {
            if (!userBlockRepository.existsByBlockerIdAndBlockedUserId(blockerId, targetUserId)) {
                com.match.SwipeAI.model.UserBlock block = com.match.SwipeAI.model.UserBlock.builder()
                        .blockerId(blockerId)
                        .blockedUserId(targetUserId)
                        .reason(reason != null && !reason.isBlank() ? reason : "Direct Block")
                        .remarks(remarks != null ? remarks.trim() : null)
                        .build();
                userBlockRepository.save(block);
            }
        } catch (Exception e) {
            log.warn("Could not save UserBlock entity: {}", e.getMessage());
        }

        // 4. Create Support / Safety ticket for Admin Review
        try {
            Profile blockerProfile = profileRepository.findById(blockerId).orElse(null);
            Profile targetProfile = profileRepository.findById(targetUserId).orElse(null);
            String blockerName = blockerProfile != null && blockerProfile.getDisplayName() != null ? blockerProfile.getDisplayName().trim() : "User " + blockerId;
            String targetName = targetProfile != null && targetProfile.getDisplayName() != null ? targetProfile.getDisplayName().trim() : "User " + targetUserId;

            StringBuilder desc = new StringBuilder();
            desc.append("ACTION: BLOCK_PROFILE\n");
            desc.append("Blocker: ").append(blockerName).append(" (ID: ").append(blockerId).append(")\n");
            desc.append("Blocked Candidate: ").append(targetName).append(" (ID: ").append(targetUserId).append(")\n");
            desc.append("Reason Category: ").append(reason != null && !reason.isBlank() ? reason : "Direct Block").append("\n");
            if (remarks != null && !remarks.isBlank()) {
                desc.append("\n--- User Remarks for Admin Review ---\n");
                desc.append(remarks.trim()).append("\n");
            }

            String ticketNumber = "BLK-" + (10000 + new Random().nextInt(90000));
            SupportTicket ticket = SupportTicket.builder()
                    .userId(blockerId)
                    .ticketNumber(ticketNumber)
                    .category(TicketCategory.SAFETY_HARASSMENT)
                    .status(TicketStatus.PENDING)
                    .subject("Profile Blocked: " + targetName + " (" + (reason != null && !reason.isBlank() ? reason : "Direct Block") + ")")
                    .description(desc.toString())
                    .build();
            supportTicketRepository.save(ticket);
        } catch (Exception e) {
            log.warn("Could not create support ticket for block: {}", e.getMessage());
        }
    }

    /**
     * Unblock a previously blocked or reported user so they can reappear in discovery.
     */
    @Transactional
    public boolean unblockUser(UUID blockerId, UUID targetUserId) {
        log.info("User {} is unblocking user {}", blockerId, targetUserId);
        try {
            userBlockRepository.deleteByBlockerIdAndBlockedUserId(blockerId, targetUserId);

            // Remove PASS interaction if one was recorded
            interactionRepository.findByActorIdAndTargetId(blockerId, targetUserId).ifPresent(interaction -> {
                if (interaction.getActionType() == ActionType.PASS) {
                    interactionRepository.delete(interaction);
                }
            });
            return true;
        } catch (Exception e) {
            log.warn("Could not unblock user {} by {}: {}", targetUserId, blockerId, e.getMessage());
            return false;
        }
    }

    /**
     * Retrieve all accounts blocked or reported by the current user.
     */
    public List<Map<String, Object>> getBlockedAndReportedUsers(UUID userId) {
        List<com.match.SwipeAI.model.UserBlock> blocks = userBlockRepository.findByBlockerIdOrderByCreatedAtDesc(userId);
        List<com.match.SwipeAI.model.UserReport> reports = userReportRepository.findByReporterIdOrderByCreatedAtDesc(userId);

        Map<UUID, Map<String, Object>> map = new LinkedHashMap<>();

        // Process blocks
        for (com.match.SwipeAI.model.UserBlock b : blocks) {
            UUID targetId = b.getBlockedUserId();
            Profile p = profileRepository.findById(targetId).orElse(null);
            User u = userRepository.findById(targetId).orElse(null);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("targetUserId", targetId.toString());
            item.put("displayName", p != null && p.getDisplayName() != null ? p.getDisplayName().trim() : "Member");
            item.put("age", u != null && u.getBirthDate() != null ? Period.between(u.getBirthDate(), LocalDate.now()).getYears() : 25);
            item.put("city", p != null && p.getCity() != null ? p.getCity().trim() : "Nearby");
            item.put("photoUrl", extractPrimaryPhoto(p));
            item.put("type", "BLOCKED");
            item.put("reason", b.getReason() != null ? b.getReason() : "Blocked by you");
            item.put("remarks", b.getRemarks());
            item.put("createdAt", b.getCreatedAt());

            map.put(targetId, item);
        }

        // Process reports (tag as REPORTED if not already or enrich)
        for (com.match.SwipeAI.model.UserReport r : reports) {
            UUID targetId = r.getReportedUserId();
            if (!map.containsKey(targetId)) {
                Profile p = profileRepository.findById(targetId).orElse(null);
                User u = userRepository.findById(targetId).orElse(null);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("targetUserId", targetId.toString());
                item.put("displayName", p != null && p.getDisplayName() != null ? p.getDisplayName().trim() : "Member");
                item.put("age", u != null && u.getBirthDate() != null ? Period.between(u.getBirthDate(), LocalDate.now()).getYears() : 25);
                item.put("city", p != null && p.getCity() != null ? p.getCity().trim() : "Nearby");
                item.put("photoUrl", extractPrimaryPhoto(p));
                item.put("type", "REPORTED");
                item.put("reason", r.getReason() != null ? r.getReason() : "Reported by you");
                item.put("remarks", r.getRemarks());
                item.put("createdAt", r.getCreatedAt());

                map.put(targetId, item);
            } else {
                map.get(targetId).put("type", "REPORTED_AND_BLOCKED");
            }
        }

        return new ArrayList<>(map.values());
    }

    private String extractPrimaryPhoto(Profile p) {
        if (p == null) return null;
        if (p.getSelfieUrl() != null && !p.getSelfieUrl().isBlank()) return p.getSelfieUrl();
        try {
            if (p.getPhotosJson() != null && !p.getPhotosJson().isBlank()) {
                List<String> list = objectMapper.readValue(p.getPhotosJson(), new TypeReference<List<String>>() {});
                if (!list.isEmpty() && list.get(0) != null) return list.get(0);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public ProfileDto.CosmicChemistryResponse getCosmicChemistry(UUID viewerId, UUID candidateId) {
        Profile viewerProfile = profileRepository.findById(viewerId).orElse(null);
        Profile candidateProfile = profileRepository.findById(candidateId).orElse(null);

        String viewerSign = viewerProfile != null ? viewerProfile.getZodiacSign() : "Taurus";
        String candidateSign = candidateProfile != null ? candidateProfile.getZodiacSign() : "Leo";
        String candidateName = candidateProfile != null ? candidateProfile.getDisplayName() : "Match";

        return cosmicChemistryEngine.generateVibeCard(viewerSign, candidateSign, candidateName);
    }

    public int calculateCompletionPercentage(User user, Profile profile) {
        if (profile == null) return 0;
        int pct = 0;

        // 1. Essential Basic Info (30% - Baseline required to unlock Discovery)
        boolean hasName = (profile.getDisplayName() != null && !profile.getDisplayName().trim().isEmpty());
        boolean hasGender = (user != null && user.getGender() != null) ||
                (profile.getGenderDisplay() != null && !profile.getGenderDisplay().trim().isEmpty());
        boolean hasOrientation = (profile.getSexualOrientation() != null && !profile.getSexualOrientation().trim().isEmpty());

        if (hasName) pct += 10;
        if (hasGender) pct += 10;
        if (hasOrientation) pct += 10;

        // 2. Dating Intent & Age / DOB (15%)
        if (user != null && user.getBirthDate() != null) pct += 8;
        if ((user != null && user.getIntent() != null) || (profile.getRelationshipIntent() != null && !profile.getRelationshipIntent().isBlank())) pct += 7;

        // 3. Bio & Prompt (15%)
        if (profile.getBio() != null && !profile.getBio().trim().isEmpty()) pct += 8;
        if (profile.getProfilePromptAnswer() != null && !profile.getProfilePromptAnswer().trim().isEmpty()) pct += 7;

        // 4. Photos & Verified Selfie (20%)
        if (profile.getPhoto1() != null && !profile.getPhoto1().trim().isEmpty()) pct += 8;
        if (profile.getPhoto2() != null && !profile.getPhoto2().trim().isEmpty()) pct += 2;
        if (profile.getPhoto3() != null && !profile.getPhoto3().trim().isEmpty()) pct += 2;
        if (profile.getPhoto4() != null && !profile.getPhoto4().trim().isEmpty()) pct += 2;
        if (profile.getPhoto5() != null && !profile.getPhoto5().trim().isEmpty()) pct += 2;
        if (profile.getSelfieUrl() != null && !profile.getSelfieUrl().trim().isEmpty()) pct += 4;

        // 5. Career, Education & Height (10%)
        if ((profile.getJob() != null && !profile.getJob().trim().isEmpty()) ||
            (profile.getOccupation() != null && !profile.getOccupation().trim().isEmpty())) pct += 4;
        if (profile.getEducation() != null && !profile.getEducation().trim().isEmpty()) pct += 3;
        if (profile.getHeight() != null && profile.getHeight() > 0) pct += 3;

        // 6. Lifestyle & Indian Context (10%)
        if (profile.getDietaryPref() != null) pct += 2;
        if (profile.getLivingStatus() != null) pct += 2;
        if ((profile.getLocation() != null && !profile.getLocation().isBlank()) ||
            (profile.getCity() != null && !profile.getCity().isBlank())) pct += 2;
        if ((profile.getSmokingHabit() != null && !profile.getSmokingHabit().isBlank()) ||
            (profile.getDrinkingHabit() != null && !profile.getDrinkingHabit().isBlank())) pct += 2;
        if (profile.getVacationPreference() != null && !profile.getVacationPreference().isBlank()) pct += 2;

        // 7. Passions / Interests (Bonus overlap up to 5%)
        if (profile.getInterests() != null && !profile.getInterests().isBlank()) pct += 3;
        if (profile.getHobbies() != null && !profile.getHobbies().isBlank()) pct += 2;

        return Math.min(100, pct);
    }

    private ProfileDto.ProfileResponse mapToResponse(User user, Profile profile) {
        int age = 0;
        LocalDate birthDate = user.getBirthDate();
        if (birthDate != null) {
            age = Period.between(birthDate, LocalDate.now()).getYears();
        }

        List<String> photos = new ArrayList<>();
        if (profile.getPhoto1() != null && !profile.getPhoto1().isBlank()) photos.add(profile.getPhoto1());
        if (profile.getPhoto2() != null && !profile.getPhoto2().isBlank()) photos.add(profile.getPhoto2());
        if (profile.getPhoto3() != null && !profile.getPhoto3().isBlank()) photos.add(profile.getPhoto3());
        if (profile.getPhoto4() != null && !profile.getPhoto4().isBlank()) photos.add(profile.getPhoto4());
        if (profile.getPhoto5() != null && !profile.getPhoto5().isBlank()) photos.add(profile.getPhoto5());
        if (profile.getPhoto6() != null && !profile.getPhoto6().isBlank()) photos.add(profile.getPhoto6());
        if (profile.getPhotosJson() != null && !profile.getPhotosJson().isBlank()) {
            try {
                List<String> fromJson = objectMapper.readValue(profile.getPhotosJson(), new TypeReference<>() {});
                for (String u : fromJson) {
                    if (u != null && !u.isBlank() && !photos.contains(u) && photos.size() < 6) {
                        photos.add(u);
                    }
                }
            } catch (Exception ignored) {}
        }

        int completionPct = calculateCompletionPercentage(user, profile);

        String verStatus = profile.getVerificationStatus();
        if (verStatus == null || verStatus.isBlank()) {
            if (Boolean.TRUE.equals(user.getFaceVerified())) {
                verStatus = "VERIFIED";
            } else if (profile.getSelfieUrl() != null && !profile.getSelfieUrl().isBlank()) {
                verStatus = "PENDING";
            } else {
                verStatus = "UNVERIFIED";
            }
        }

        boolean isRetryNeeded = Boolean.TRUE.equals(user.getNeedsLivenessRetry())
                || "REJECTED".equalsIgnoreCase(verStatus)
                || "RETRY_NEEDED".equalsIgnoreCase(verStatus);
        String liveStatus = user.getLivenessStatus() != null ? user.getLivenessStatus()
                : (isRetryNeeded ? "RETRY_NEEDED" : (Boolean.TRUE.equals(user.getFaceVerified()) ? "VERIFIED" : "PENDING"));

        return ProfileDto.ProfileResponse.builder()
                .userId(user.getId())
                .phoneE164(user.getPhoneE164())
                .email(user.getEmail())
                .marketingOptIn(Boolean.TRUE.equals(user.getMarketingOptIn()))
                .displayName(profile.getDisplayName() != null ? profile.getDisplayName() : "")
                .fullName(profile.getDisplayName() != null ? profile.getDisplayName() : "")
                .bio(profile.getBio())
                .age(age)
                .birthDate(birthDate)
                .gender(user.getGender())
                .intent(user.getIntent())
                .digilockerVerified(Boolean.TRUE.equals(user.getDigilockerVerified()))
                .whatsappVerified(Boolean.TRUE.equals(user.getWhatsappVerified()))
                .faceVerified(Boolean.TRUE.equals(user.getFaceVerified()))
                .needsLivenessRetry(isRetryNeeded)
                .livenessStatus(liveStatus)
                .verificationStatus(verStatus)
                .livenessScore(user.getLivenessScore() != null ? user.getLivenessScore() : 0.0)
                .karmaScore(user.getKarmaScore() != null ? user.getKarmaScore() : 100)
                .dietaryPref(profile.getDietaryPref())
                .livingStatus(profile.getLivingStatus())
                .languagesSpoken(profile.getLanguagesSpoken())
                .zodiacSign(profile.getZodiacSign())
                .sunSign(profile.getSunSign())
                .moonSign(profile.getMoonSign())
                .voicePromptUrl(profile.getVoicePromptUrl())
                .voicePromptDuration(profile.getVoicePromptDuration())
                .voicePromptText(profile.getVoicePromptText())
                .company(profile.getCompany())
                .occupation(profile.getOccupation())
                .job(profile.getJob() != null ? profile.getJob() : profile.getOccupation())
                .education(profile.getEducation())
                .institute(profile.getInstitute())
                .interests(profile.getInterests())
                .height(profile.getHeight())
                .location(profile.getLocation())
                .maxDistanceKm(profile.getMaxDistanceKm())
                .latitude(user.getLatitude())
                .longitude(user.getLongitude())
                .sexualOrientation(profile.getSexualOrientation())
                .showOrientationOnProfile(profile.getShowOrientationOnProfile())
                .genderDisplay(profile.getGenderDisplay())
                .showGenderOnProfile(profile.getShowGenderOnProfile())
                .genderPreferenceDisplay(profile.getGenderPreferenceDisplay())
                .relationshipIntent(profile.getRelationshipIntent())
                .profilePromptQuestion(profile.getProfilePromptQuestion())
                .profilePromptAnswer(profile.getProfilePromptAnswer())
                .photo1(profile.getPhoto1())
                .photo2(profile.getPhoto2())
                .photo3(profile.getPhoto3())
                .photo4(profile.getPhoto4())
                .photo5(profile.getPhoto5())
                .photo6(profile.getPhoto6())
                .selfieUrl(profile.getSelfieUrl())
                .smokingHabit(profile.getSmokingHabit())
                .drinkingHabit(profile.getDrinkingHabit())
                .hobbies(profile.getHobbies())
                .vacationPreference(profile.getVacationPreference())
                .city(profile.getCity())
                .neighborhood(profile.getNeighborhood())
                .microCircle(profile.getMicroCircle())
                .microCircles(extractUserMicroCircles(profile))
                .photos(photos)
                .completionPercentage(completionPct)
                .sparksBalance(user.getSparksBalance() != null ? user.getSparksBalance() : 0)
                .boostsBalance(user.getBoostsBalance() != null ? user.getBoostsBalance() : 0)
                .directDmsBalance(user.getDirectDmsBalance() != null ? user.getDirectDmsBalance() : 0)
                .hasActivePass(Boolean.TRUE.equals(user.getHasActivePass()))
                .selectedMemeUrl(profile.getSelectedMemeUrl())
                .selectedMemeTitle(profile.getSelectedMemeTitle())
                .photosVerificationJson(profile.getPhotosVerificationJson())
                .build();
    }

    public List<String> extractUserMicroCircles(Profile profile) {
        if (profile == null) return List.of();
        List<String> circles = new ArrayList<>();
        if (profile.getMicroCirclesJson() != null && !profile.getMicroCirclesJson().isBlank()) {
            try {
                List<String> parsed = objectMapper.readValue(profile.getMicroCirclesJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                if (parsed != null) {
                    for (String c : parsed) {
                        if (c != null && !c.isBlank()) circles.add(c.trim());
                    }
                }
            } catch (Exception ignored) {}
        }
        if (circles.isEmpty() && profile.getMicroCircle() != null && !profile.getMicroCircle().isBlank()) {
            String[] parts = profile.getMicroCircle().split("[,;|]+");
            for (String p : parts) {
                if (!p.isBlank()) circles.add(p.trim());
            }
        }
        return circles.stream().distinct().limit(5).toList();
    }

    public void triggerAsyncSelfieVerification(UUID userId) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                // Allow DB transaction to commit and give user visible PENDING state before background completion
                Thread.sleep(2000);
                User u = userRepository.findById(userId).orElse(null);
                Profile p = profileRepository.findById(userId).orElse(null);
                if (u == null || p == null) return;

                String selfie = p.getSelfieUrl();
                if (selfie == null || selfie.isBlank()) {
                    return;
                }

                List<FaceMatchService.PhotoSlotItemDto> photoSlots = new ArrayList<>();
                if (p.getPhoto1() != null && !p.getPhoto1().isBlank()) {
                    photoSlots.add(FaceMatchService.PhotoSlotItemDto.builder().slot(1).photoUrl(p.getPhoto1().trim()).build());
                }
                if (p.getPhoto2() != null && !p.getPhoto2().isBlank()) {
                    photoSlots.add(FaceMatchService.PhotoSlotItemDto.builder().slot(2).photoUrl(p.getPhoto2().trim()).build());
                }
                if (p.getPhoto3() != null && !p.getPhoto3().isBlank()) {
                    photoSlots.add(FaceMatchService.PhotoSlotItemDto.builder().slot(3).photoUrl(p.getPhoto3().trim()).build());
                }
                if (p.getPhoto4() != null && !p.getPhoto4().isBlank()) {
                    photoSlots.add(FaceMatchService.PhotoSlotItemDto.builder().slot(4).photoUrl(p.getPhoto4().trim()).build());
                }
                if (p.getPhoto5() != null && !p.getPhoto5().isBlank()) {
                    photoSlots.add(FaceMatchService.PhotoSlotItemDto.builder().slot(5).photoUrl(p.getPhoto5().trim()).build());
                }
                if (p.getPhoto6() != null && !p.getPhoto6().isBlank()) {
                    photoSlots.add(FaceMatchService.PhotoSlotItemDto.builder().slot(6).photoUrl(p.getPhoto6().trim()).build());
                }

                if (photoSlots.isEmpty() && p.getPhotosJson() != null && !p.getPhotosJson().isBlank()) {
                    try {
                        List<String> parsed = objectMapper.readValue(p.getPhotosJson(), new TypeReference<>() {});
                        int s = 1;
                        for (String url : parsed) {
                            if (url != null && !url.isBlank()) {
                                photoSlots.add(FaceMatchService.PhotoSlotItemDto.builder().slot(s++).photoUrl(url.trim()).build());
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (photoSlots.isEmpty()) {
                    // Check selfie quality when no profile photos have been uploaded yet
                    var selfCheck = faceMatchService.compareFaces(selfie, selfie);
                    User freshUser = userRepository.findById(userId).orElse(u);
                    Profile freshProfile = profileRepository.findById(userId).orElse(p);
                    if (selfCheck.isMatch() || selfCheck.getSelfieFacesDetected() >= 1) {
                        freshUser.setNeedsLivenessRetry(false);
                        freshUser.setLivenessStatus("VERIFIED");
                        if (freshUser.getLivenessScore() == null || freshUser.getLivenessScore() < 0.85) {
                            freshUser.setLivenessScore(0.95);
                        }
                        // FaceVerified remains false until primary profile photos are uploaded and matched
                        freshUser.setFaceVerified(false);
                        userRepository.save(freshUser);
                        freshProfile.setVerificationStatus("PENDING_PHOTOS");
                        profileRepository.save(freshProfile);
                        log.info("Selfie face quality check PASSED for user {} (waiting for profile photos)", userId);
                    } else {
                        freshUser.setFaceVerified(false);
                        freshUser.setNeedsLivenessRetry(true);
                        freshUser.setLivenessStatus("RETRY_NEEDED");
                        userRepository.save(freshUser);
                        freshProfile.setVerificationStatus("REJECTED");
                        profileRepository.save(freshProfile);
                        log.warn("Selfie face quality check REJECTED (no face in selfie) for user {}", userId);
                    }
                    return;
                }

                // Run biometric verification across ALL uploaded photo slots in parallel
                var batchRes = faceMatchService.matchAllPhotos(selfie, photoSlots);

                // Serialize per-photo verification results into JSON ledger
                String ledgerJson = null;
                try {
                    ledgerJson = objectMapper.writeValueAsString(batchRes.getResults());
                } catch (Exception e) {
                    log.warn("Failed to serialize photos verification ledger for user {}: {}", userId, e.getMessage());
                }

                // Re-fetch fresh entities so we never overwrite concurrent photo updates made while batch matching ran
                User freshUser = userRepository.findById(userId).orElse(u);
                Profile freshProfile = profileRepository.findById(userId).orElse(p);
                freshProfile.setPhotosVerificationJson(ledgerJson);

                // Auto-promote matched photo if primary failed (< 60% match threshold)
                boolean primarySlotPassed = false;
                if (batchRes.getResults() != null) {
                    FaceMatchService.PhotoSlotVerificationResultDto slot1Res = batchRes.getResults().stream()
                            .filter(r -> r.getSlot() == 1)
                            .findFirst().orElse(null);
                    primarySlotPassed = slot1Res != null && slot1Res.isMatch() && slot1Res.getConfidencePercent() >= 60.0;

                    // If slot 1 failed (< 60%), but an alternative slot (e.g. slot 2) matched >= 60%:
                    if (!primarySlotPassed) {
                        FaceMatchService.PhotoSlotVerificationResultDto bestAlternative = batchRes.getResults().stream()
                                .filter(r -> r.getSlot() > 1 && r.isMatch() && r.getConfidencePercent() >= 60.0)
                                .max(Comparator.comparingDouble(FaceMatchService.PhotoSlotVerificationResultDto::getConfidencePercent))
                                .orElse(null);

                        if (bestAlternative != null && bestAlternative.getPhotoUrl() != null && !bestAlternative.getPhotoUrl().isBlank()) {
                            log.info("Auto-promoting verified photo from slot {} (confidence: {}%) to primary photo1 for user {}",
                                    bestAlternative.getSlot(), bestAlternative.getConfidencePercent(), userId);

                            String oldPhoto1 = freshProfile.getPhoto1();
                            String newPhoto1 = bestAlternative.getPhotoUrl();

                            freshProfile.setPhoto1(newPhoto1);
                            if (bestAlternative.getSlot() == 2) freshProfile.setPhoto2(oldPhoto1);
                            else if (bestAlternative.getSlot() == 3) freshProfile.setPhoto3(oldPhoto1);
                            else if (bestAlternative.getSlot() == 4) freshProfile.setPhoto4(oldPhoto1);
                            else if (bestAlternative.getSlot() == 5) freshProfile.setPhoto5(oldPhoto1);
                            else if (bestAlternative.getSlot() == 6) freshProfile.setPhoto6(oldPhoto1);

                            List<String> syncPhotos = new ArrayList<>();
                            if (freshProfile.getPhoto1() != null && !freshProfile.getPhoto1().isBlank()) syncPhotos.add(freshProfile.getPhoto1());
                            if (freshProfile.getPhoto2() != null && !freshProfile.getPhoto2().isBlank()) syncPhotos.add(freshProfile.getPhoto2());
                            if (freshProfile.getPhoto3() != null && !freshProfile.getPhoto3().isBlank()) syncPhotos.add(freshProfile.getPhoto3());
                            if (freshProfile.getPhoto4() != null && !freshProfile.getPhoto4().isBlank()) syncPhotos.add(freshProfile.getPhoto4());
                            if (freshProfile.getPhoto5() != null && !freshProfile.getPhoto5().isBlank()) syncPhotos.add(freshProfile.getPhoto5());
                            if (freshProfile.getPhoto6() != null && !freshProfile.getPhoto6().isBlank()) syncPhotos.add(freshProfile.getPhoto6());
                            try {
                                freshProfile.setPhotosJson(objectMapper.writeValueAsString(syncPhotos));
                            } catch (Exception ignored) {}

                            primarySlotPassed = true;
                        }
                    }
                }

                if (batchRes.isProfileVerified() || primarySlotPassed) {
                    freshUser.setFaceVerified(true);
                    freshUser.setNeedsLivenessRetry(false);
                    freshUser.setLivenessStatus("VERIFIED");
                    if (freshUser.getLivenessScore() == null || freshUser.getLivenessScore() < 0.85) {
                        freshUser.setLivenessScore(0.95);
                    }
                    userRepository.save(freshUser);
                    freshProfile.setVerificationStatus("VERIFIED");
                    profileRepository.save(freshProfile);
                    log.info("Multi-photo selfie verification PASSED for user {}: verified real photo active in primary slot",
                            userId);
                } else {
                    freshUser.setFaceVerified(false);
                    freshUser.setNeedsLivenessRetry(false);
                    freshUser.setLivenessStatus("PRIMARY_PHOTO_FAILED");
                    freshProfile.setVerificationStatus("REJECTED");
                    userRepository.save(freshUser);
                    profileRepository.save(freshProfile);
                    log.warn("Multi-photo selfie verification REJECTED for user {}: primary photos matched under 60% threshold.", userId);
                }
            } catch (Exception e) {
                log.error("Async multi-photo selfie verification failed for user {}: {}", userId, e.getMessage());
            }
        });
    }

    private String sanitize(String value, int maxLen) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }

    private String sanitizePhotoUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("file:") || trimmed.startsWith("content:")) {
            return null;
        }
        return trimmed;
    }
}
