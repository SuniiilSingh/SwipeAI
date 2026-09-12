package com.match.SwipeAI.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.SwipeAI.dto.ProfileDto;
import com.match.SwipeAI.model.Profile;
import com.match.SwipeAI.model.User;
import com.match.SwipeAI.repository.ProfileRepository;
import com.match.SwipeAI.repository.UserRepository;
import com.match.SwipeAI.service.engine.CosmicChemistryEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final CosmicChemistryEngine cosmicChemistryEngine;
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
        userRepository.save(user);

        Profile profile = profileRepository.findById(userId)
                .orElseGet(() -> Profile.builder().userId(user.getId()).build());

        if (request.getDisplayName() != null) profile.setDisplayName(request.getDisplayName());
        if (request.getFullName() != null) profile.setDisplayName(request.getFullName());
        if (request.getBio() != null) profile.setBio(request.getBio());
        if (request.getDietaryPref() != null) profile.setDietaryPref(request.getDietaryPref());
        if (request.getLivingStatus() != null) profile.setLivingStatus(request.getLivingStatus());
        if (request.getLanguagesSpoken() != null) profile.setLanguagesSpoken(request.getLanguagesSpoken());
        if (request.getZodiacSign() != null) profile.setZodiacSign(request.getZodiacSign());
        if (request.getSunSign() != null) profile.setSunSign(request.getSunSign());
        if (request.getMoonSign() != null) profile.setMoonSign(request.getMoonSign());
        if (request.getCompany() != null) profile.setCompany(request.getCompany());
        if (request.getOccupation() != null) profile.setOccupation(request.getOccupation());
        if (request.getJob() != null) profile.setJob(request.getJob());
        if (request.getEducation() != null) profile.setEducation(request.getEducation());
        if (request.getInterests() != null) profile.setInterests(request.getInterests());
        if (request.getHeight() != null) profile.setHeight(request.getHeight());
        if (request.getLocation() != null) profile.setLocation(request.getLocation());
        if (request.getMaxDistanceKm() != null) profile.setMaxDistanceKm(request.getMaxDistanceKm());
        if (request.getSexualOrientation() != null) profile.setSexualOrientation(request.getSexualOrientation());
        if (request.getShowOrientationOnProfile() != null) profile.setShowOrientationOnProfile(request.getShowOrientationOnProfile());
        if (request.getGenderDisplay() != null) profile.setGenderDisplay(request.getGenderDisplay());
        if (request.getShowGenderOnProfile() != null) profile.setShowGenderOnProfile(request.getShowGenderOnProfile());
        if (request.getGenderPreferenceDisplay() != null) profile.setGenderPreferenceDisplay(request.getGenderPreferenceDisplay());
        if (request.getRelationshipIntent() != null) profile.setRelationshipIntent(request.getRelationshipIntent());
        if (request.getProfilePromptQuestion() != null) profile.setProfilePromptQuestion(request.getProfilePromptQuestion());
        if (request.getProfilePromptAnswer() != null) profile.setProfilePromptAnswer(request.getProfilePromptAnswer());
        if (request.getPhoto1() != null) profile.setPhoto1(request.getPhoto1());
        if (request.getPhoto2() != null) profile.setPhoto2(request.getPhoto2());
        if (request.getPhoto3() != null) profile.setPhoto3(request.getPhoto3());
        if (request.getPhoto4() != null) profile.setPhoto4(request.getPhoto4());
        if (request.getPhoto5() != null) profile.setPhoto5(request.getPhoto5());
        if (request.getPhoto6() != null) profile.setPhoto6(request.getPhoto6());
        if (request.getSelfieUrl() != null) profile.setSelfieUrl(request.getSelfieUrl());
        if (request.getSmokingHabit() != null) profile.setSmokingHabit(request.getSmokingHabit());
        if (request.getDrinkingHabit() != null) profile.setDrinkingHabit(request.getDrinkingHabit());
        if (request.getHobbies() != null) profile.setHobbies(request.getHobbies());
        if (request.getVacationPreference() != null) profile.setVacationPreference(request.getVacationPreference());
        if (request.getCity() != null) profile.setCity(request.getCity());
        if (request.getNeighborhood() != null) profile.setNeighborhood(request.getNeighborhood());
        if (request.getMicroCircle() != null) profile.setMicroCircle(request.getMicroCircle());

        if (request.getPhotos() != null && !request.getPhotos().isEmpty()) {
            try {
                profile.setPhotosJson(objectMapper.writeValueAsString(request.getPhotos()));
            } catch (Exception e) {
                profile.setPhotosJson("[]");
            }
        } else {
            List<String> syncPhotos = new ArrayList<>();
            if (profile.getPhoto1() != null && !profile.getPhoto1().isBlank()) syncPhotos.add(profile.getPhoto1());
            if (profile.getPhoto2() != null && !profile.getPhoto2().isBlank()) syncPhotos.add(profile.getPhoto2());
            if (profile.getPhoto3() != null && !profile.getPhoto3().isBlank()) syncPhotos.add(profile.getPhoto3());
            if (profile.getPhoto4() != null && !profile.getPhoto4().isBlank()) syncPhotos.add(profile.getPhoto4());
            if (profile.getPhoto5() != null && !profile.getPhoto5().isBlank()) syncPhotos.add(profile.getPhoto5());
            if (profile.getPhoto6() != null && !profile.getPhoto6().isBlank()) syncPhotos.add(profile.getPhoto6());
            if (!syncPhotos.isEmpty()) {
                try {
                    profile.setPhotosJson(objectMapper.writeValueAsString(syncPhotos));
                } catch (Exception ignored) {}
            }
        }

        profile = profileRepository.save(profile);
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
        profileRepository.deleteById(userId);
        userRepository.deleteById(userId);
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
        int pct = 0;
        if (profile.getDisplayName() != null && !profile.getDisplayName().trim().isEmpty()) pct += 10;
        if (user.getBirthDate() != null) pct += 10;
        if (user.getGender() != null) pct += 10;
        if (user.getIntent() != null || profile.getRelationshipIntent() != null) pct += 10;
        if (profile.getBio() != null && !profile.getBio().trim().isEmpty()) pct += 10;
        if ((profile.getJob() != null && !profile.getJob().trim().isEmpty()) ||
            (profile.getOccupation() != null && !profile.getOccupation().trim().isEmpty())) pct += 10;
        if (profile.getEducation() != null && !profile.getEducation().trim().isEmpty()) pct += 10;

        if (profile.getInterests() != null && !profile.getInterests().trim().isEmpty()) {
            String[] split = profile.getInterests().split(",");
            int count = (int) Arrays.stream(split).map(String::trim).filter(s -> !s.isEmpty()).count();
            if (count >= 3) {
                pct += 10;
            } else if (count > 0) {
                pct += count * 3;
            }
        }

        if (profile.getPhoto1() != null && !profile.getPhoto1().trim().isEmpty()) pct += 5;
        if (profile.getPhoto2() != null && !profile.getPhoto2().trim().isEmpty()) pct += 5;
        if (profile.getPhoto3() != null && !profile.getPhoto3().trim().isEmpty()) pct += 5;
        if (profile.getPhoto4() != null && !profile.getPhoto4().trim().isEmpty()) pct += 5;
        if (profile.getPhoto5() != null && !profile.getPhoto5().trim().isEmpty()) pct += 5;
        if (profile.getPhoto6() != null && !profile.getPhoto6().trim().isEmpty()) pct += 5;
        if (profile.getSelfieUrl() != null && !profile.getSelfieUrl().trim().isEmpty()) pct += 5;

        return Math.min(100, pct);
    }

    private ProfileDto.ProfileResponse mapToResponse(User user, Profile profile) {
        int age = 24;
        if (user.getBirthDate() != null) {
            age = Period.between(user.getBirthDate(), LocalDate.now()).getYears();
        }

        List<String> photos = new ArrayList<>();
        if (profile.getPhotosJson() != null && !profile.getPhotosJson().isBlank()) {
            try {
                photos = objectMapper.readValue(profile.getPhotosJson(), new TypeReference<>() {});
            } catch (Exception ignored) {}
        }
        if (photos.isEmpty()) {
            if (profile.getPhoto1() != null && !profile.getPhoto1().isBlank()) photos.add(profile.getPhoto1());
            if (profile.getPhoto2() != null && !profile.getPhoto2().isBlank()) photos.add(profile.getPhoto2());
            if (profile.getPhoto3() != null && !profile.getPhoto3().isBlank()) photos.add(profile.getPhoto3());
            if (profile.getPhoto4() != null && !profile.getPhoto4().isBlank()) photos.add(profile.getPhoto4());
            if (profile.getPhoto5() != null && !profile.getPhoto5().isBlank()) photos.add(profile.getPhoto5());
            if (profile.getPhoto6() != null && !profile.getPhoto6().isBlank()) photos.add(profile.getPhoto6());
        }

        int completionPct = calculateCompletionPercentage(user, profile);

        return ProfileDto.ProfileResponse.builder()
                .userId(user.getId())
                .phoneE164(user.getPhoneE164())
                .displayName(profile.getDisplayName() != null ? profile.getDisplayName() : "")
                .fullName(profile.getDisplayName() != null ? profile.getDisplayName() : "")
                .bio(profile.getBio())
                .age(age)
                .gender(user.getGender())
                .intent(user.getIntent())
                .digilockerVerified(Boolean.TRUE.equals(user.getDigilockerVerified()))
                .whatsappVerified(Boolean.TRUE.equals(user.getWhatsappVerified()))
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
                .interests(profile.getInterests())
                .height(profile.getHeight())
                .location(profile.getLocation())
                .maxDistanceKm(profile.getMaxDistanceKm())
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
                .build();
    }
}
