package com.match.SwipeAI.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.SwipeAI.dto.DiscoveryDto;
import com.match.SwipeAI.enums.*;
import com.match.SwipeAI.model.*;
import com.match.SwipeAI.repository.*;
import com.match.SwipeAI.service.engine.MultiObjectiveMatchEngine;
import com.match.SwipeAI.service.engine.ShadowShieldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final InteractionRepository interactionRepository;
    private final MatchRepository matchRepository;
    private final ShadowShieldService shadowShieldService;
    private final MultiObjectiveMatchEngine matchEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int DAILY_HARD_CAP = 25;

    public DiscoveryDto.DiscoveryFeedResponse getDiscoveryFeed(UUID viewerId, DiscoveryDto.DiscoveryFeedRequest request) {
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Profile viewerProfile = profileRepository.findById(viewerId).orElse(null);

        // 1. Calculate daily swipe count
        OffsetDateTime startOfDay = OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0);
        long swipesToday = interactionRepository.countByActorIdAndCreatedAtAfter(viewerId, startOfDay);
        int remainingSwipes = Boolean.TRUE.equals(viewer.getHasActivePass()) ?
                999 : Math.max(0, DAILY_HARD_CAP - (int) swipesToday);

        // 2. Fetch candidates excluding already interacted users
        List<Interaction> pastInteractions = interactionRepository.findByActorId(viewerId);
        Set<UUID> interactedUserIds = new HashSet<>();
        interactedUserIds.add(viewerId); // exclude self
        for (Interaction i : pastInteractions) {
            interactedUserIds.add(i.getTargetId());
        }

        List<User> allUsers = userRepository.findAll();
        List<DiscoveryDto.CandidateCardDto> candidateCards = new ArrayList<>();

        for (User candidate : allUsers) {
            if (interactedUserIds.contains(candidate.getId())) continue;
            if (Boolean.TRUE.equals(candidate.getIsIncognito())) continue;

            // Opposite gender / non-binary matching
            if (viewer.getGender() != null && candidate.getGender() != null && viewer.getGender() == candidate.getGender()) {
                continue;
            }

            Profile candidateProfile = profileRepository.findById(candidate.getId()).orElse(null);

            // 3. Shadow Shield Check (Family, Relatives, Boss, Corporate Domain)
            if (shadowShieldService.isShielded(viewer, viewerProfile, candidate, candidateProfile)) {
                log.debug("Shadow Shield prevented candidate {} from appearing to viewer {}", candidate.getId(), viewerId);
                continue;
            }

            // 4. Filter by Micro-Circle if requested
            if (request.getMicroCircle() != null && !request.getMicroCircle().isBlank()) {
                if (candidateProfile == null || !request.getMicroCircle().equalsIgnoreCase(candidateProfile.getMicroCircle())) {
                    continue;
                }
            }

            // 5. Distance and Multi-Objective Compatibility Score
            double distanceKm = matchEngine.calculateDistanceKm(
                    viewer.getLatitude(), viewer.getLongitude(),
                    candidate.getLatitude(), candidate.getLongitude()
            );

            int compScore = matchEngine.calculateCompatibilityScore(
                    viewer, viewerProfile,
                    candidate, candidateProfile,
                    distanceKm
            );

            candidateCards.add(buildCandidateCard(candidate, candidateProfile, distanceKm, compScore));
        }

        // Sort descending by multi-objective compatibility score
        candidateCards.sort((a, b) -> Integer.compare(b.getCompatibilityScore(), a.getCompatibilityScore()));

        // Limit results
        int limit = request.getLimit() != null ? request.getLimit() : 10;
        List<DiscoveryDto.CandidateCardDto> limitedCandidates = candidateCards.stream().limit(limit).toList();

        return DiscoveryDto.DiscoveryFeedResponse.builder()
                .status("success")
                .data(DiscoveryDto.FeedData.builder()
                        .remainingDailySwipes(remainingSwipes)
                        .dailyHardCap(DAILY_HARD_CAP)
                        .candidates(limitedCandidates)
                        .build())
                .build();
    }

    @Transactional
    public DiscoveryDto.InteractionResponse recordInteraction(UUID actorId, DiscoveryDto.InteractionRequest request) {
        User actor = userRepository.findById(actorId).orElseThrow();
        User target = userRepository.findById(request.getTargetId()).orElseThrow();

        // Check daily limit if not VIP
        OffsetDateTime startOfDay = OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0);
        long swipesToday = interactionRepository.countByActorIdAndCreatedAtAfter(actorId, startOfDay);
        if (!Boolean.TRUE.equals(actor.getHasActivePass()) && swipesToday >= DAILY_HARD_CAP) {
            throw new IllegalStateException("Daily swipe limit of 25 reached. Unlock Weekend Pass for unlimited likes!");
        }

        // Save interaction
        Interaction interaction = Interaction.builder()
                .actorId(actorId)
                .targetId(request.getTargetId())
                .actionType(request.getActionType())
                .contextType(request.getContextType())
                .contextTargetId(request.getContextTargetId())
                .commentText(request.getCommentText())
                .build();

        interactionRepository.save(interaction);

        boolean isMatch = false;
        UUID matchId = null;

        // If LIKE or SUPER_CHAI, check if target liked actor previously
        if (request.getActionType() == ActionType.LIKE || request.getActionType() == ActionType.SUPER_CHAI) {
            Optional<Interaction> reciprocal = interactionRepository.findByActorIdAndTargetId(request.getTargetId(), actorId);
            if (reciprocal.isPresent() &&
                    (reciprocal.get().getActionType() == ActionType.LIKE || reciprocal.get().getActionType() == ActionType.SUPER_CHAI)) {
                // Form new Match in PENDING_ICEBREAKER state with 48h timer
                isMatch = true;
                Match match = Match.builder()
                        .userAId(actorId)
                        .userBId(request.getTargetId())
                        .initiatorId(actorId)
                        .status(MatchStatus.PENDING_ICEBREAKER)
                        .expiresAt(OffsetDateTime.now().plusHours(48))
                        .messagesCount(0)
                        .icebreakerGameData("{\"quizId\":\"quiz_sunday_vibe\",\"title\":\"10s Rapid-Fire Quiz\",\"question\":\"Your Ultimate Sunday Vibe:\",\"options\":[\"Filter Coffee & Dosa crawl in Indiranagar\",\"Sleep until 2 PM & binge true-crime podcasts\",\"Spontaneous drive to Nandi Hills\"],\"userAAnswer\":null,\"userBAnswer\":null,\"isCompleted\":false,\"isMutualAgreement\":false}")
                        .build();

                match = matchRepository.save(match);
                matchId = match.getId();
                log.info("Mutual Like! Created Match {} between {} and {}", matchId, actorId, request.getTargetId());
            }
        }

        int remaining = Boolean.TRUE.equals(actor.getHasActivePass()) ?
                999 : Math.max(0, DAILY_HARD_CAP - (int) (swipesToday + 1));

        return DiscoveryDto.InteractionResponse.builder()
                .isMatch(isMatch)
                .matchId(matchId)
                .message(isMatch ? "It's a Vibe Match! Unlock chat lounge via the 10s Icebreaker Quiz." : "Interaction recorded.")
                .remainingDailySwipes(remaining)
                .build();
    }

    public List<DiscoveryDto.CircleDto> getMicroCircles() {
        return List.of(
                DiscoveryDto.CircleDto.builder()
                        .id("koramangala-tech")
                        .name("Koramangala Tech Founders")
                        .description("Early stage builders, product designers & VC analysts")
                        .activeMembers(1420)
                        .icon("laptop-outline")
                        .build(),
                DiscoveryDto.CircleDto.builder()
                        .id("dmrc-yellow-line")
                        .name("DMRC Yellow Line Commuters")
                        .description("Gurgaon to Hauz Khas daily podcast listeners")
                        .activeMembers(2890)
                        .icon("subway-outline")
                        .build(),
                DiscoveryDto.CircleDto.builder()
                        .id("indie-music")
                        .name("Indie Music & Festival Goers")
                        .description("NH7 Weekender, Prateek Kuhad, Peter Cat Recording Co.")
                        .activeMembers(1850)
                        .icon("musical-notes-outline")
                        .build(),
                DiscoveryDto.CircleDto.builder()
                        .id("dog-parents")
                        .name("Dog Parents & Pet Lovers")
                        .description("Cubbon park Sunday dog meetup regulars")
                        .activeMembers(980)
                        .icon("paw-outline")
                        .build()
        );
    }

    private DiscoveryDto.CandidateCardDto buildCandidateCard(User user, Profile profile, double distanceKm, int compScore) {
        int age = 24;
        if (user.getBirthDate() != null) {
            age = Period.between(user.getBirthDate(), LocalDate.now()).getYears();
        }

        List<String> photos = new java.util.ArrayList<>();
        if (profile != null && profile.getPhotosJson() != null && !profile.getPhotosJson().isBlank()) {
            try {
                photos = objectMapper.readValue(profile.getPhotosJson(), new TypeReference<>() {});
            } catch (Exception ignored) {}
        }
        if (photos.isEmpty() && profile != null) {
            if (profile.getPhoto1() != null && !profile.getPhoto1().isBlank()) photos.add(profile.getPhoto1());
            if (profile.getPhoto2() != null && !profile.getPhoto2().isBlank()) photos.add(profile.getPhoto2());
            if (profile.getPhoto3() != null && !profile.getPhoto3().isBlank()) photos.add(profile.getPhoto3());
            if (profile.getPhoto4() != null && !profile.getPhoto4().isBlank()) photos.add(profile.getPhoto4());
            if (profile.getPhoto5() != null && !profile.getPhoto5().isBlank()) photos.add(profile.getPhoto5());
            if (profile.getPhoto6() != null && !profile.getPhoto6().isBlank()) photos.add(profile.getPhoto6());
        }
        if (photos.isEmpty()) {
            photos = List.of("https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500");
        }

        String photo1 = !photos.isEmpty() ? photos.get(0) : (profile != null ? profile.getPhoto1() : null);
        String photo2 = photos.size() > 1 ? photos.get(1) : (profile != null ? profile.getPhoto2() : null);
        String photo3 = photos.size() > 2 ? photos.get(2) : (profile != null ? profile.getPhoto3() : null);

        String voiceUrl = profile != null ? profile.getVoicePromptUrl() : null;
        String promptText = profile != null ? profile.getVoicePromptText() : "My controversial chai opinion";
        if (promptText == null) promptText = "My controversial chai opinion";

        return DiscoveryDto.CandidateCardDto.builder()
                .userId(user.getId())
                .displayName(profile != null && profile.getDisplayName() != null ? profile.getDisplayName() : "Single in City")
                .fullName(profile != null && profile.getDisplayName() != null ? profile.getDisplayName() : "Single in City")
                .age(age)
                .isDigilockerVerified(Boolean.TRUE.equals(user.getDigilockerVerified()))
                .isWhatsappVerified(Boolean.TRUE.equals(user.getWhatsappVerified()))
                .livenessScore(user.getLivenessScore() != null ? user.getLivenessScore() : 0.98)
                .distanceKm(distanceKm)
                .culturalBadges(DiscoveryDto.CulturalBadges.builder()
                        .diet(profile != null && profile.getDietaryPref() != null ? profile.getDietaryPref() : DietaryPreference.PURE_VEG)
                        .living(profile != null && profile.getLivingStatus() != null ? profile.getLivingStatus() : LivingStatus.INDEPENDENT_FLAT)
                        .languages(profile != null && profile.getLanguagesSpoken() != null ? profile.getLanguagesSpoken() : List.of("English", "Hindi"))
                        .zodiac(profile != null && profile.getZodiacSign() != null ? profile.getZodiacSign() : "Leo")
                        .build())
                .voicePrompt(DiscoveryDto.VoicePromptDto.builder()
                        .audioUrl(voiceUrl != null ? voiceUrl : "https://cdn.swipeai.in/audio/chai_opinion.m4a")
                        .durationSec(profile != null && profile.getVoicePromptDuration() != null ? profile.getVoicePromptDuration() : 14)
                        .promptText(promptText)
                        .build())
                .memeMatch(DiscoveryDto.MemeMatchDto.builder()
                        .matchPercent(88)
                        .memeTitle("Bangalore Silk Board Peak Hour")
                        .memeImageUrl("https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500")
                        .build())
                .cosmicChemistry(DiscoveryDto.CosmicChemistryDto.builder()
                        .synergyTag("89% Weekend Vibe Match")
                        .score(89)
                        .build())
                .compatibilityScore(compScore)
                .bio(profile != null && profile.getBio() != null ? profile.getBio() : "")
                .company(profile != null ? profile.getCompany() : null)
                .occupation(profile != null ? profile.getOccupation() : null)
                .job(profile != null ? (profile.getJob() != null ? profile.getJob() : profile.getOccupation()) : null)
                .education(profile != null ? profile.getEducation() : null)
                .height(profile != null ? profile.getHeight() : null)
                .interests(profile != null ? profile.getInterests() : null)
                .sexualOrientation(profile != null ? profile.getSexualOrientation() : null)
                .genderDisplay(profile != null ? profile.getGenderDisplay() : null)
                .relationshipIntent(profile != null ? profile.getRelationshipIntent() : (user.getIntent() != null ? user.getIntent().name() : null))
                .profilePromptQuestion(profile != null ? profile.getProfilePromptQuestion() : null)
                .profilePromptAnswer(profile != null ? profile.getProfilePromptAnswer() : null)
                .sunSign(profile != null ? profile.getSunSign() : null)
                .moonSign(profile != null ? profile.getMoonSign() : null)
                .karmaScore(user.getKarmaScore() != null ? user.getKarmaScore() : 180)
                .smokingHabit(profile != null ? profile.getSmokingHabit() : null)
                .drinkingHabit(profile != null ? profile.getDrinkingHabit() : null)
                .hobbies(profile != null ? profile.getHobbies() : null)
                .vacationPreference(profile != null ? profile.getVacationPreference() : null)
                .city(profile != null ? profile.getCity() : "Bengaluru")
                .neighborhood(profile != null ? profile.getNeighborhood() : "Indiranagar")
                .microCircle(profile != null ? profile.getMicroCircle() : "Koramangala Tech Founders")
                .photos(photos)
                .photo1(photo1)
                .photo2(photo2)
                .photo3(photo3)
                .build();
    }
}
