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
        if (request.getLatitude() != null) user.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) user.setLongitude(request.getLongitude());
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
        if (request.getInstitute() != null) profile.setInstitute(request.getInstitute());
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
        if (request.getPhoto1() != null) profile.setPhoto1(request.getPhoto1().trim().isEmpty() ? null : request.getPhoto1().trim());
        if (request.getPhoto2() != null) profile.setPhoto2(request.getPhoto2().trim().isEmpty() ? null : request.getPhoto2().trim());
        if (request.getPhoto3() != null) profile.setPhoto3(request.getPhoto3().trim().isEmpty() ? null : request.getPhoto3().trim());
        if (request.getPhoto4() != null) profile.setPhoto4(request.getPhoto4().trim().isEmpty() ? null : request.getPhoto4().trim());
        if (request.getPhoto5() != null) profile.setPhoto5(request.getPhoto5().trim().isEmpty() ? null : request.getPhoto5().trim());
        if (request.getPhoto6() != null) profile.setPhoto6(request.getPhoto6().trim().isEmpty() ? null : request.getPhoto6().trim());
        if (request.getSelfieUrl() != null) profile.setSelfieUrl(request.getSelfieUrl().trim().isEmpty() ? null : request.getSelfieUrl().trim());
        if (request.getSmokingHabit() != null) profile.setSmokingHabit(request.getSmokingHabit());
        if (request.getDrinkingHabit() != null) profile.setDrinkingHabit(request.getDrinkingHabit());
        if (request.getHobbies() != null) profile.setHobbies(request.getHobbies());
        if (request.getVacationPreference() != null) profile.setVacationPreference(request.getVacationPreference());
        if (request.getCity() != null) profile.setCity(request.getCity());
        if (request.getNeighborhood() != null) profile.setNeighborhood(request.getNeighborhood());
        if (request.getMicroCircle() != null) profile.setMicroCircle(request.getMicroCircle());
        if (request.getVoicePromptUrl() != null) profile.setVoicePromptUrl(request.getVoicePromptUrl().trim().isEmpty() ? null : request.getVoicePromptUrl().trim());
        if (request.getVoicePromptDuration() != null) profile.setVoicePromptDuration(request.getVoicePromptDuration());
        if (request.getVoicePromptText() != null) profile.setVoicePromptText(request.getVoicePromptText());
        if (request.getSelectedMemeUrl() != null) profile.setSelectedMemeUrl(request.getSelectedMemeUrl().trim().isEmpty() ? null : request.getSelectedMemeUrl().trim());
        if (request.getSelectedMemeTitle() != null) profile.setSelectedMemeTitle(request.getSelectedMemeTitle());

        if (request.getPhotos() != null) {
            try {
                List<String> cleanPhotos = request.getPhotos().stream()
                        .filter(p -> p != null && !p.trim().isEmpty())
                        .toList();
                profile.setPhotosJson(objectMapper.writeValueAsString(cleanPhotos));
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
            try {
                profile.setPhotosJson(objectMapper.writeValueAsString(syncPhotos));
            } catch (Exception ignored) {}
        }

        profile = profileRepository.save(profile);
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
                .birthDate(birthDate)
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
}
