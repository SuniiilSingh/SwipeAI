package com.match.SwipeAI.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.SwipeAI.dto.ProfileDto;
import com.match.SwipeAI.model.Profile;
import com.match.SwipeAI.model.User;
import com.match.SwipeAI.repository.*;
import com.match.SwipeAI.service.engine.CosmicChemistryEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.match.SwipeAI.enums.ActionType;
import com.match.SwipeAI.enums.MatchStatus;
import com.match.SwipeAI.enums.TicketCategory;
import com.match.SwipeAI.enums.TicketStatus;
import com.match.SwipeAI.model.Interaction;
import com.match.SwipeAI.model.Match;
import com.match.SwipeAI.model.SupportTicket;

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
    private final com.match.SwipeAI.service.engine.MatchKarmaService karmaService;
    private final CosmicChemistryEngine cosmicChemistryEngine;
    private final com.match.SwipeAI.service.integration.FaceMatchService faceMatchService;
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
        if (request.getMicroCircle() != null) profile.setMicroCircle(sanitize(request.getMicroCircle(), 100));
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
        log.warn("User {} reported candidate {} for reason: {}", reporterId, targetUserId, reason);

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

        // 4. Create Support / Trust & Safety moderation ticket
        try {
            String ticketNumber = "UGC-" + (10000 + new Random().nextInt(90000));
            SupportTicket ticket = SupportTicket.builder()
                    .userId(reporterId)
                    .ticketNumber(ticketNumber)
                    .category(TicketCategory.SAFETY_HARASSMENT)
                    .status(TicketStatus.PENDING)
                    .subject("Profile Report: " + (reason != null ? reason : "Objectionable Content"))
                    .description("Reporter ID: " + reporterId + "\nReported Target User ID: " + targetUserId + "\nReason: " + reason)
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
        log.info("User {} blocked target {}", blockerId, targetUserId);

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
                .photos(photos)
                .completionPercentage(completionPct)
                .sparksBalance(user.getSparksBalance() != null ? user.getSparksBalance() : 0)
                .boostsBalance(user.getBoostsBalance() != null ? user.getBoostsBalance() : 0)
                .directDmsBalance(user.getDirectDmsBalance() != null ? user.getDirectDmsBalance() : 0)
                .hasActivePass(Boolean.TRUE.equals(user.getHasActivePass()))
                .selectedMemeUrl(profile.getSelectedMemeUrl())
                .selectedMemeTitle(profile.getSelectedMemeTitle())
                .build();
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

                List<String> candidatePhotos = new ArrayList<>();
                if (p.getPhoto1() != null && !p.getPhoto1().isBlank()) candidatePhotos.add(p.getPhoto1().trim());
                if (p.getPhoto2() != null && !p.getPhoto2().isBlank()) candidatePhotos.add(p.getPhoto2().trim());
                if (p.getPhoto3() != null && !p.getPhoto3().isBlank()) candidatePhotos.add(p.getPhoto3().trim());
                if (p.getPhoto4() != null && !p.getPhoto4().isBlank()) candidatePhotos.add(p.getPhoto4().trim());
                if (p.getPhoto5() != null && !p.getPhoto5().isBlank()) candidatePhotos.add(p.getPhoto5().trim());
                if (p.getPhoto6() != null && !p.getPhoto6().isBlank()) candidatePhotos.add(p.getPhoto6().trim());

                if (candidatePhotos.isEmpty() && p.getPhotosJson() != null && !p.getPhotosJson().isBlank()) {
                    try {
                        List<String> parsed = objectMapper.readValue(p.getPhotosJson(), new TypeReference<>() {});
                        for (String url : parsed) {
                            if (url != null && !url.isBlank()) candidatePhotos.add(url.trim());
                        }
                    } catch (Exception ignored) {}
                }

                if (candidatePhotos.isEmpty()) {
                    // Verify selfie face quality in background via YuNet/SFace even before profile photos are uploaded
                    var selfCheck = faceMatchService.compareFaces(selfie, selfie);
                    // Re-fetch fresh entities so we never overwrite photos uploaded while faceMatchService was running
                    User freshUser = userRepository.findById(userId).orElse(u);
                    Profile freshProfile = profileRepository.findById(userId).orElse(p);
                    if (selfCheck.isMatch() || selfCheck.getSelfieFacesDetected() >= 1) {
                        freshUser.setFaceVerified(true);
                        if (freshUser.getLivenessScore() == null || freshUser.getLivenessScore() < 0.85) {
                            freshUser.setLivenessScore(0.95);
                        }
                        userRepository.save(freshUser);
                        freshProfile.setVerificationStatus("VERIFIED");
                        profileRepository.save(freshProfile);
                        log.info("Async selfie verification PASSED (selfie face check) for user {}", userId);
                    } else {
                        freshUser.setFaceVerified(false);
                        userRepository.save(freshUser);
                        freshProfile.setVerificationStatus("REJECTED");
                        profileRepository.save(freshProfile);
                        log.info("Async selfie verification REJECTED (no clear face in selfie) for user {}", userId);
                    }
                    return;
                }

                boolean anyMatch = false;
                double bestScore = 0.0;
                for (String photoUrl : candidatePhotos) {
                    try {
                        var res = faceMatchService.compareFaces(selfie, photoUrl);
                        if (res.getSimilarityScore() > bestScore) {
                            bestScore = res.getSimilarityScore();
                        }
                        if (res.isMatch()) {
                            anyMatch = true;
                            break;
                        }
                    } catch (Exception e) {
                        log.warn("Async face compare error for user {} against {}: {}", userId, photoUrl, e.getMessage());
                    }
                }

                // Re-fetch fresh entities so we never overwrite concurrent photo updates made while compareFaces ran
                User freshUser = userRepository.findById(userId).orElse(u);
                Profile freshProfile = profileRepository.findById(userId).orElse(p);
                if (anyMatch) {
                    freshUser.setFaceVerified(true);
                    freshUser.setLivenessScore(Math.max(freshUser.getLivenessScore() != null ? freshUser.getLivenessScore() : 0.0, bestScore));
                    userRepository.save(freshUser);
                    freshProfile.setVerificationStatus("VERIFIED");
                    profileRepository.save(freshProfile);
                    log.info("Async selfie verification PASSED for user {} (score={})", userId, bestScore);
                } else {
                    freshUser.setFaceVerified(false);
                    userRepository.save(freshUser);
                    freshProfile.setVerificationStatus("REJECTED");
                    profileRepository.save(freshProfile);
                    log.info("Async selfie verification REJECTED for user {} (bestScore={})", userId, bestScore);
                }
            } catch (Exception e) {
                log.error("Async selfie verification failed for user {}: {}", userId, e.getMessage());
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
