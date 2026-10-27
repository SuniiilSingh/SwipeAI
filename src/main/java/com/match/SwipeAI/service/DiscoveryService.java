package com.match.SwipeAI.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.SwipeAI.dto.DiscoveryDto;
import com.match.SwipeAI.enums.*;
import com.match.SwipeAI.model.*;
import com.match.SwipeAI.repository.*;
import com.match.SwipeAI.service.engine.MultiObjectiveMatchEngine;
import com.match.SwipeAI.service.engine.ShadowShieldService;
import com.match.SwipeAI.service.integration.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

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
    private final ProfileService profileService;
    private final DesireProfileRepository desireProfileRepository;
    private final DesireProfileService desireProfileService;
    private final PushNotificationService pushNotificationService;
    private final CandidateSearchRepository candidateSearchRepository;
    private final MicroCommunityRepository microCommunityRepository;
    private final VedicAstrologyService astrologyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int DAILY_HARD_CAP = 25;

    public DiscoveryDto.DiscoveryFeedResponse getDiscoveryFeed(UUID viewerId, DiscoveryDto.DiscoveryFeedRequest request) {
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Profile viewerProfile = profileRepository.findById(viewerId).orElse(null);
        DesireProfile viewerDesire = desireProfileRepository.findById(viewerId).orElse(null);

        // 1. Calculate daily swipe count
        OffsetDateTime startOfDay = OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0);
        long swipesToday = interactionRepository.countByActorIdAndCreatedAtAfter(viewerId, startOfDay);
        int remainingSwipes = Boolean.TRUE.equals(viewer.getHasActivePass()) ?
                999 : Math.max(0, DAILY_HARD_CAP - (int) swipesToday);

        // 2. Ensure viewerProfile exists
        if (viewerProfile == null) {
            viewerProfile = Profile.builder()
                    .userId(viewerId)
                    .displayName("New Member")
                    .build();
            viewerProfile = profileRepository.save(viewerProfile);
        }

        boolean hasConfiguredDesire = viewerDesire != null && Boolean.TRUE.equals(viewerDesire.getIsConfigured());

        // 3. Determine viewer's effective search radius limit in km:
        // When desire profile is configured, prefer viewerDesire.maxDistanceKm; otherwise use profile radius or 50 km default.
        double maxRadiusKm = 50.0;
        if (hasConfiguredDesire && viewerDesire.getMaxDistanceKm() != null && viewerDesire.getMaxDistanceKm() > 0) {
            maxRadiusKm = viewerDesire.getMaxDistanceKm().doubleValue();
        } else if (viewerProfile != null && viewerProfile.getMaxDistanceKm() != null && viewerProfile.getMaxDistanceKm() > 0) {
            maxRadiusKm = viewerProfile.getMaxDistanceKm().doubleValue();
        } else if (request.getMaxDistanceKm() != null && request.getMaxDistanceKm() > 0) {
            maxRadiusKm = request.getMaxDistanceKm();
        }

        // Sync fresh GPS coordinates from request if provided
        Double viewerLat = request.getLatitude() != null ? request.getLatitude() : viewer.getLatitude();
        Double viewerLon = request.getLongitude() != null ? request.getLongitude() : viewer.getLongitude();
        if (request.getLatitude() != null && request.getLongitude() != null) {
            if (!request.getLatitude().equals(viewer.getLatitude()) || !request.getLongitude().equals(viewer.getLongitude())) {
                viewer.setLatitude(request.getLatitude());
                viewer.setLongitude(request.getLongitude());
                userRepository.save(viewer);
            }
        }

        // 4. Resolve Target Gender pushdown criteria
        boolean isViewerFemale = viewer.getGender() == Gender.FEMALE ||
                (viewerProfile != null && viewerProfile.getGenderDisplay() != null &&
                        (viewerProfile.getGenderDisplay().equalsIgnoreCase("Woman") ||
                         viewerProfile.getGenderDisplay().equalsIgnoreCase("Women") ||
                         viewerProfile.getGenderDisplay().equalsIgnoreCase("Female")));

        boolean isViewerMale = viewer.getGender() == Gender.MALE ||
                (viewerProfile != null && viewerProfile.getGenderDisplay() != null &&
                        (viewerProfile.getGenderDisplay().equalsIgnoreCase("Man") ||
                         viewerProfile.getGenderDisplay().equalsIgnoreCase("Men") ||
                         viewerProfile.getGenderDisplay().equalsIgnoreCase("Male")));

        String viewerPref = (viewerProfile != null && viewerProfile.getGenderPreferenceDisplay() != null)
                ? viewerProfile.getGenderPreferenceDisplay().trim().toLowerCase()
                : null;

        Gender targetGender = null;
        List<String> targetGenderDisplays = null;
        if ("women".equals(viewerPref) || "woman".equals(viewerPref) || "female".equals(viewerPref)) {
            targetGender = Gender.FEMALE;
            targetGenderDisplays = List.of("woman", "women", "female");
        } else if ("men".equals(viewerPref) || "man".equals(viewerPref) || "male".equals(viewerPref)) {
            targetGender = Gender.MALE;
            targetGenderDisplays = List.of("man", "men", "male");
        } else if ("everyone".equals(viewerPref)) {
            // Allows all genders
        } else {
            // Unset preference: default to opposite gender matching
            if (isViewerMale) {
                targetGender = Gender.FEMALE;
                targetGenderDisplays = List.of("woman", "women", "female");
            } else if (isViewerFemale) {
                targetGender = Gender.MALE;
                targetGenderDisplays = List.of("man", "men", "male");
            }
        }

        // 5. Compute Spatial Bounding Box coordinates
        Double minLat = null, maxLat = null, minLon = null, maxLon = null;
        if (viewerLat != null && viewerLon != null && maxRadiusKm > 0) {
            double latDelta = maxRadiusKm / 111.0;
            minLat = viewerLat - latDelta;
            maxLat = viewerLat + latDelta;

            double cosLat = Math.cos(Math.toRadians(viewerLat));
            double lonDelta = (cosLat > 0.0001) ? (maxRadiusKm / (111.0 * cosLat)) : 180.0;
            minLon = viewerLon - lonDelta;
            maxLon = viewerLon + lonDelta;
        }

        // 6. Compute Age range pushdown criteria from Desire Profile
        LocalDate minBirthDate = null, maxBirthDate = null;
        if (hasConfiguredDesire && (viewerDesire.getMinAge() != null || viewerDesire.getMaxAge() != null)) {
            int minAge = viewerDesire.getMinAge() != null ? viewerDesire.getMinAge() : 18;
            int maxAge = viewerDesire.getMaxAge() != null ? viewerDesire.getMaxAge() : 99;
            boolean isFlexible = Boolean.TRUE.equals(viewerDesire.getAgeFlexible());
            int lowerBound = isFlexible ? Math.max(18, minAge - 2) : minAge;
            int upperBound = isFlexible ? maxAge + 2 : maxAge;

            LocalDate today = LocalDate.now();
            minBirthDate = today.minusYears(upperBound + 1).plusDays(1);
            maxBirthDate = today.minusYears(lowerBound);
        }

        // 7. Resolve Dietary pushdown criteria
        List<DietaryPreference> dietaryPreferences = null;
        if (request.getDietaryFilters() != null && !request.getDietaryFilters().isEmpty()) {
            Set<DietaryPreference> expanded = new HashSet<>();
            for (DietaryPreference filter : request.getDietaryFilters()) {
                if (filter == DietaryPreference.PURE_VEG) {
                    expanded.add(DietaryPreference.PURE_VEG);
                    expanded.add(DietaryPreference.STRICT_JAIN);
                    expanded.add(DietaryPreference.VEGAN);
                } else {
                    expanded.add(filter);
                }
            }
            dietaryPreferences = new ArrayList<>(expanded);
        } else if (hasConfiguredDesire && viewerDesire.getDietaryHarmony() != null) {
            String desireDiet = viewerDesire.getDietaryHarmony();
            if ("STRICT_JAIN_ONLY".equalsIgnoreCase(desireDiet)) {
                dietaryPreferences = List.of(DietaryPreference.STRICT_JAIN);
            } else if ("VEG_SPECTRUM".equalsIgnoreCase(desireDiet) || "EGGETARIAN_OR_VEG".equalsIgnoreCase(desireDiet)) {
                dietaryPreferences = List.of(
                        DietaryPreference.PURE_VEG,
                        DietaryPreference.STRICT_JAIN,
                        DietaryPreference.VEGAN,
                        DietaryPreference.EGGETARIAN
                );
            }
        }

        // 8. Execute Database Candidate Query with Bounded Limit
        int requestedLimit = request.getLimit() != null ? request.getLimit() : 10;
        int fetchLimit = Math.max(50, requestedLimit * 5);

        String explicitCommunity = request.getMicroCircle();
        List<String> viewerCommunities = new ArrayList<>(extractUserMicroCircles(viewerProfile));
        if (explicitCommunity != null && !explicitCommunity.isBlank()) {
            String explicitClean = explicitCommunity.trim();
            if (!viewerCommunities.contains(explicitClean)) {
                viewerCommunities.add(0, explicitClean);
            }
        }
        String userCommunity = (viewerProfile != null && viewerProfile.getMicroCircle() != null && !viewerProfile.getMicroCircle().isBlank())
                ? viewerProfile.getMicroCircle() : null;

        CandidateSearchRepository.Criteria candidateCriteria = CandidateSearchRepository.Criteria.builder()
                .viewerId(viewerId)
                .targetGender(targetGender)
                .targetGenderDisplays(targetGenderDisplays)
                .minLat(minLat)
                .maxLat(maxLat)
                .minLon(minLon)
                .maxLon(maxLon)
                .minBirthDate(minBirthDate)
                .maxBirthDate(maxBirthDate)
                .microCircle(explicitCommunity)
                .userCommunity(userCommunity)
                .dietaryPreferences(dietaryPreferences)
                .limit(fetchLimit)
                .build();

        List<CandidateSearchRepository.CandidateUserRecord> eligibleCandidates = candidateSearchRepository.searchCandidates(candidateCriteria);
        List<DiscoveryDto.CandidateCardDto> candidateCards = new ArrayList<>();

        for (CandidateSearchRepository.CandidateUserRecord candidateRecord : eligibleCandidates) {
            User candidate = candidateRecord.user();
            Profile candidateProfile = candidateRecord.profile();

            // 2. Strict Gender & Interest Matching (e.g. Male looking for Women -> only Women shown)
            if (!isGenderAndInterestMatch(viewer, viewerProfile, candidate, candidateProfile)) {
                continue;
            }

            // 2b. Photo Verification & Catfish Filter:
            // Do not show candidate if their photos are unverified, rejected, or found to be a mismatch!
            if ("REJECTED".equalsIgnoreCase(candidateProfile != null ? candidateProfile.getVerificationStatus() : "")
                    || "MISMATCH_DETECTED".equalsIgnoreCase(candidate.getLivenessStatus())
                    || "PRIMARY_PHOTO_FAILED".equalsIgnoreCase(candidate.getLivenessStatus())) {
                log.debug("Excluding candidate {} from feed due to photo mismatch / unverified status", candidate.getId());
                continue;
            }

            // 3. Shadow Shield Check (Family, Relatives, Boss, Corporate Domain)
            if (shadowShieldService.isShielded(viewer, viewerProfile, candidate, candidateProfile)) {
                log.debug("Shadow Shield prevented candidate {} from appearing to viewer {}", candidate.getId(), viewerId);
                continue;
            }

            // 4. Micro-Community Match Detection (Supports up to 5 joined communities on both sides)
            List<String> candidateCommunities = extractUserMicroCircles(candidateProfile);
            boolean isSharedCommunity = hasSharedCommunityMatch(viewerCommunities, candidateCommunities);

            // 4a. Filter by Dietary Preferences if requested in feed request
            if (request.getDietaryFilters() != null && !request.getDietaryFilters().isEmpty()) {
                DietaryPreference candidateDiet = (candidateProfile != null && candidateProfile.getDietaryPref() != null)
                        ? candidateProfile.getDietaryPref()
                        : DietaryPreference.PURE_VEG;

                boolean dietMatches = false;
                for (DietaryPreference filter : request.getDietaryFilters()) {
                    if (filter == DietaryPreference.PURE_VEG) {
                        // Strict Jain and Vegan are purely vegetarian as well
                        if (candidateDiet == DietaryPreference.PURE_VEG ||
                            candidateDiet == DietaryPreference.STRICT_JAIN ||
                            candidateDiet == DietaryPreference.VEGAN) {
                            dietMatches = true;
                            break;
                        }
                    } else if (filter == DietaryPreference.STRICT_JAIN) {
                        if (candidateDiet == DietaryPreference.STRICT_JAIN) {
                            dietMatches = true;
                            break;
                        }
                    } else if (filter == DietaryPreference.VEGAN) {
                        if (candidateDiet == DietaryPreference.VEGAN) {
                            dietMatches = true;
                            break;
                        }
                    } else if (filter == DietaryPreference.EGGETARIAN) {
                        if (candidateDiet == DietaryPreference.EGGETARIAN) {
                            dietMatches = true;
                            break;
                        }
                    } else if (filter == DietaryPreference.NON_VEG) {
                        if (candidateDiet == DietaryPreference.NON_VEG) {
                            dietMatches = true;
                            break;
                        }
                    } else if (candidateDiet == filter) {
                        dietMatches = true;
                        break;
                    }
                }
                if (!dietMatches) {
                    continue;
                }
            } else if (hasConfiguredDesire && viewerDesire.getDietaryHarmony() != null) {
                // When Desire Profile is configured and no explicit tags requested, enforce Desire Dietary Harmony
                String desireDiet = viewerDesire.getDietaryHarmony();
                DietaryPreference candidateDiet = candidateProfile != null ? candidateProfile.getDietaryPref() : DietaryPreference.PURE_VEG;
                if ("STRICT_JAIN_ONLY".equalsIgnoreCase(desireDiet)) {
                    if (candidateDiet != DietaryPreference.STRICT_JAIN) {
                        continue;
                    }
                } else if ("VEG_SPECTRUM".equalsIgnoreCase(desireDiet)) {
                    if (candidateDiet == DietaryPreference.NON_VEG) {
                        continue;
                    }
                } else if ("EGGETARIAN_OR_VEG".equalsIgnoreCase(desireDiet)) {
                    if (candidateDiet == DietaryPreference.NON_VEG) {
                        continue;
                    }
                }
            }

            // 5. Candidate Age & Desire Age Boundaries
            int candidateAge = 24;
            if (candidate.getBirthDate() != null) {
                candidateAge = Period.between(candidate.getBirthDate(), LocalDate.now()).getYears();
            }

            if (hasConfiguredDesire) {
                int minAge = viewerDesire.getMinAge() != null ? viewerDesire.getMinAge() : 18;
                int maxAge = viewerDesire.getMaxAge() != null ? viewerDesire.getMaxAge() : 99;
                boolean isFlexible = Boolean.TRUE.equals(viewerDesire.getAgeFlexible());
                int lowerBound = isFlexible ? Math.max(18, minAge - 2) : minAge;
                int upperBound = isFlexible ? maxAge + 2 : maxAge;
                if (candidateAge < lowerBound || candidateAge > upperBound) {
                    log.debug("Candidate {} excluded: age {} outside desire range [{}, {}]", candidate.getId(), candidateAge, lowerBound, upperBound);
                    continue;
                }
            }

            // 6. Accurate Distance Calculation with City/Neighborhood Context
            double distanceKm = matchEngine.calculateDistanceWithContext(
                    viewerLat, viewerLon, viewerProfile,
                    candidate.getLatitude(), candidate.getLongitude(), candidateProfile
            );

            // Strict Search Radius Limit: Only match profiles within effective radius limit
            // ADD-ON EXCEPTION: Shared community members bypass the geographic distance cap!
            if (!isSharedCommunity && distanceKm > maxRadiusKm) {
                log.debug("Candidate {} excluded: distance {} km exceeds max radius {} km",
                        candidate.getId(), distanceKm, maxRadiusKm);
                continue;
            }

            // 7. Multi-Objective Compatibility Score Calculation
            // For shared community matches across cities, cap distance decay to 5km to preserve high synergy score
            double effectiveDistForScoring = isSharedCommunity ? Math.min(5.0, distanceKm) : distanceKm;
            int compScore;
            Integer desireScorePercent = null;
            List<String> desireHighlights = List.of();

            if (hasConfiguredDesire) {
                DesireProfileService.DesireMatchResult desireResult = desireProfileService.calculateDesireMatch(
                        viewerDesire, candidateProfile, candidateAge, effectiveDistForScoring
                );
                desireScorePercent = desireResult.scorePercent();
                desireHighlights = desireResult.highlights();

                // Multi-objective blended score factoring in desire blueprint
                compScore = matchEngine.calculateCompatibilityScoreWithDesire(
                        viewer, viewerProfile,
                        candidate, candidateProfile,
                        effectiveDistForScoring,
                        desireScorePercent
                );
            } else {
                // Earlier approach: pure baseline compatibility without desire consideration
                compScore = matchEngine.calculateCompatibilityScore(
                        viewer, viewerProfile,
                        candidate, candidateProfile,
                        effectiveDistForScoring
                );
            }

            // Micro-Community Synergy Bonus (+15% if viewer and candidate belong to the same circle)
            if (isSharedCommunity) {
                compScore = Math.min(99, compScore + 15);
            }

            // Incoming Like Priority Bonus: If candidate already liked the viewer, boost their position to top of feed
            Optional<Interaction> incomingLike = interactionRepository.findByActorIdAndTargetId(candidate.getId(), viewerId);
            if (incomingLike.isPresent() && (
                    incomingLike.get().getActionType() == ActionType.LIKE ||
                    incomingLike.get().getActionType() == ActionType.SUPER_CHAI ||
                    incomingLike.get().getActionType() == ActionType.SUPER_SPARK)) {
                compScore = Math.min(100, compScore + 30);
            }

            candidateCards.add(buildCandidateCard(viewerId, candidate, candidateProfile, distanceKm, compScore,
                    desireScorePercent, desireHighlights, isSharedCommunity));
        }

        // Fallback: If no candidate matched strict filters/bounding box, return all available active profiles (except self)
        if (candidateCards.isEmpty()) {
            List<Profile> allProfiles = profileRepository.findAll();
            for (Profile p : allProfiles) {
                if (p.getUserId() != null && p.getUserId().equals(viewerId)) continue;

                List<String> candidateCommunities = extractUserMicroCircles(p);
                // If user requested a specific micro-community circle, strictly filter fallback by that circle
                if (request.getMicroCircle() != null && !request.getMicroCircle().isBlank()) {
                    if (!hasSharedCommunityMatch(List.of(request.getMicroCircle().trim()), candidateCommunities)) {
                        continue;
                    }
                }

                User candidateUser = userRepository.findById(p.getUserId()).orElse(null);
                if (candidateUser == null) continue;
                if (!isGenderAndInterestMatch(viewer, viewerProfile, candidateUser, p)) continue;
                if (shadowShieldService.isShielded(viewer, viewerProfile, candidateUser, p)) continue;

                boolean isShared = hasSharedCommunityMatch(viewerCommunities, candidateCommunities);

                candidateCards.add(buildCandidateCard(viewerId, candidateUser, p, 3.5, 88, 88, List.of("High Synergy", "Local Circle"), isShared));
            }
        }

        // Sort: Shared community members and high compatibility first, then closest distance
        candidateCards.sort(
                Comparator.comparing((DiscoveryDto.CandidateCardDto c) -> Boolean.TRUE.equals(c.getIsSharedCommunity()) ? 0 : 1)
                        .thenComparing(Comparator.comparingDouble(DiscoveryDto.CandidateCardDto::getDistanceKm))
                        .thenComparing(Comparator.comparingInt(DiscoveryDto.CandidateCardDto::getCompatibilityScore).reversed())
        );

        // Limit results (up to requested limit or 50 by default to provide all candidates within search radius)
        int limit = request.getLimit() != null ? request.getLimit() : 50;
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

        // Save or update interaction (upsert to handle re-swipes / retries without unique constraint violations)
        Optional<Interaction> existingOpt = interactionRepository.findByActorIdAndTargetId(actorId, request.getTargetId());
        Interaction interaction;
        if (existingOpt.isPresent()) {
            interaction = existingOpt.get();
            interaction.setActionType(request.getActionType());
            interaction.setContextType(request.getContextType());
            interaction.setContextTargetId(request.getContextTargetId());
            interaction.setCommentText(request.getCommentText());
            interaction.setCreatedAt(OffsetDateTime.now());
        } else {
            interaction = Interaction.builder()
                    .actorId(actorId)
                    .targetId(request.getTargetId())
                    .actionType(request.getActionType())
                    .contextType(request.getContextType())
                    .contextTargetId(request.getContextTargetId())
                    .commentText(request.getCommentText())
                    .build();
        }
        interactionRepository.save(interaction);

        boolean isMatch = false;
        UUID matchId = null;

        // If LIKE, SUPER_CHAI, or SUPER_SPARK, check if target liked actor previously or if match exists
        boolean isHighIntent = request.getActionType() == ActionType.LIKE ||
                request.getActionType() == ActionType.SUPER_CHAI ||
                request.getActionType() == ActionType.SUPER_SPARK;

        if (isHighIntent) {
            Optional<Match> existingMatch = matchRepository.findMatchBetween(actorId, request.getTargetId());
            if (existingMatch.isPresent() && existingMatch.get().getStatus() != MatchStatus.UNMATCHED) {
                isMatch = true;
                matchId = existingMatch.get().getId();
            } else {
                Optional<Interaction> reciprocal = interactionRepository.findByActorIdAndTargetId(request.getTargetId(), actorId);
                boolean reciprocalIsHighIntent = reciprocal.isPresent() && (
                        reciprocal.get().getActionType() == ActionType.LIKE ||
                        reciprocal.get().getActionType() == ActionType.SUPER_CHAI ||
                        reciprocal.get().getActionType() == ActionType.SUPER_SPARK
                );
                if (reciprocalIsHighIntent) {
                    // Rule of 5: check active match capacity for both users
                    long actorActive = matchRepository.countActiveRuleOfFiveMatches(actorId);
                    long targetActive = matchRepository.countActiveRuleOfFiveMatches(request.getTargetId());
                    boolean atCapacity = actorActive >= 5 || targetActive >= 5;
                    MatchStatus initialStatus = atCapacity ? MatchStatus.QUEUED_CAPACITY : MatchStatus.PENDING_ICEBREAKER;

                    isMatch = true;
                    Match match = Match.builder()
                            .userAId(actorId)
                            .userBId(request.getTargetId())
                            .initiatorId(actorId)
                            .status(initialStatus)
                            .expiresAt(OffsetDateTime.now().plusHours(72))
                            .messagesCount(0)
                            .e2eeSecret(UUID.randomUUID().toString())
                            .icebreakerGameData("{\"quizId\":\"quiz_sunday_vibe\",\"title\":\"10s Rapid-Fire Quiz\",\"question\":\"Your Ultimate Sunday Vibe:\",\"options\":[\"Filter Coffee & Dosa crawl in Indiranagar\",\"Sleep until 2 PM & binge true-crime podcasts\",\"Spontaneous drive to Nandi Hills\"],\"userAAnswer\":null,\"userBAnswer\":null,\"isCompleted\":false,\"isMutualAgreement\":false}")
                            .build();

                    match = matchRepository.save(match);
                    matchId = match.getId();
                    log.info("Mutual Like! Created Match {} ({}) between {} and {}", matchId, initialStatus, actorId, request.getTargetId());

                    // Dispatch push notification to both users
                    String actorName = profileRepository.findById(actorId).map(Profile::getDisplayName).orElse("Your match");
                    String targetName = profileRepository.findById(request.getTargetId()).map(Profile::getDisplayName).orElse("Your match");
                    pushNotificationService.sendMatchNotification(actorId, request.getTargetId(), matchId, actorName, targetName);
                } else {
                    // Single-sided like: actor liked target, target has not liked actor yet
                    String actorName = profileRepository.findById(actorId).map(Profile::getDisplayName).orElse("Someone");
                    pushNotificationService.sendLikeNotification(actorId, request.getTargetId(), actorName, request.getActionType().name());
                }
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
        return getMicroCircles(null);
    }

    public List<DiscoveryDto.CircleDto> getMicroCircles(String city) {
        List<MicroCommunity> list;
        if (city != null && !city.trim().isEmpty()) {
            String trimmed = city.trim();
            if (trimmed.equalsIgnoreCase("Delhi NCR") || trimmed.equalsIgnoreCase("Delhi")) {
                list = microCommunityRepository.findByCitiesIgnoreCase(List.of("delhi ncr", "delhi", "gurgaon", "gurugram", "noida", "greater noida"));
            } else if (trimmed.equalsIgnoreCase("Gurgaon") || trimmed.equalsIgnoreCase("Gurugram")) {
                list = microCommunityRepository.findByCitiesIgnoreCase(List.of("gurgaon", "gurugram"));
            } else if (trimmed.equalsIgnoreCase("Noida") || trimmed.equalsIgnoreCase("Greater Noida")) {
                list = microCommunityRepository.findByCitiesIgnoreCase(List.of("noida", "greater noida"));
            } else {
                list = microCommunityRepository.findByCityIgnoreCaseOrderByIsPopularDescNameAsc(trimmed);
            }
        } else {
            list = microCommunityRepository.findAllByOrderByCityAscNameAsc();
        }

        if (list == null || list.isEmpty()) {
            return List.of();
        }

        return list.stream().map(c -> DiscoveryDto.CircleDto.builder()
                .id(c.getId() != null ? c.getId().toString() : c.getSlug())
                .city(c.getCity())
                .name(c.getName())
                .slug(c.getSlug())
                .tagline(c.getTagline())
                .vibeCategory(c.getVibeCategory())
                .badgeIcon(c.getBadgeIcon())
                .description(c.getTagline())
                .activeMembers(c.getActiveMembersCount() != null ? c.getActiveMembersCount() : 120)
                .icon(c.getIconName() != null ? c.getIconName() : "people-outline")
                .isPopular(Boolean.TRUE.equals(c.getIsPopular()))
                .build()
        ).collect(Collectors.toList());
    }

    public DiscoveryDto.CandidateCardDto buildCandidateCard(User user, Profile profile, double distanceKm, int compScore) {
        return buildCandidateCard(null, user, profile, distanceKm, compScore, 88, List.of("High vibe alignment ✨"), false);
    }

    public DiscoveryDto.CandidateCardDto buildCandidateCard(User user, Profile profile, double distanceKm, int compScore,
                                                             Integer desireMatchPercent, List<String> desireMatchHighlights) {
        return buildCandidateCard(null, user, profile, distanceKm, compScore, desireMatchPercent, desireMatchHighlights, false);
    }

    public DiscoveryDto.CandidateCardDto buildCandidateCard(UUID viewerId, User user, Profile profile, double distanceKm, int compScore,
                                                             Integer desireMatchPercent, List<String> desireMatchHighlights) {
        return buildCandidateCard(viewerId, user, profile, distanceKm, compScore, desireMatchPercent, desireMatchHighlights, false);
    }

    public DiscoveryDto.CandidateCardDto buildCandidateCard(UUID viewerId, User user, Profile profile, double distanceKm, int compScore,
                                                             Integer desireMatchPercent, List<String> desireMatchHighlights, boolean isSharedCommunity) {
        int age = 24;
        if (user.getBirthDate() != null) {
            age = Period.between(user.getBirthDate(), LocalDate.now()).getYears();
        }

        List<String> photos = new java.util.ArrayList<>();
        if (profile != null && profile.getPhotosJson() != null && !profile.getPhotosJson().isBlank()) {
            try {
                List<String> parsed = objectMapper.readValue(profile.getPhotosJson(), new TypeReference<>() {});
                if (parsed != null) {
                    for (String p : parsed) {
                        if (p != null && !p.isBlank() && !p.startsWith("file:") && !p.startsWith("content:")) {
                            photos.add(p.trim());
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (photos.isEmpty() && profile != null) {
            java.util.List<String> rawSlots = java.util.List.of(
                profile.getPhoto1() != null ? profile.getPhoto1() : "",
                profile.getPhoto2() != null ? profile.getPhoto2() : "",
                profile.getPhoto3() != null ? profile.getPhoto3() : "",
                profile.getPhoto4() != null ? profile.getPhoto4() : "",
                profile.getPhoto5() != null ? profile.getPhoto5() : "",
                profile.getPhoto6() != null ? profile.getPhoto6() : ""
            );
            for (String p : rawSlots) {
                if (!p.isBlank() && !p.startsWith("file:") && !p.startsWith("content:")) {
                    photos.add(p.trim());
                }
            }
        }

        // Zero-Catfish Policy: Filter out any photos flagged as MISMATCH from discovery feed
        if (profile != null && profile.getPhotosVerificationJson() != null && !profile.getPhotosVerificationJson().isBlank()) {
            try {
                List<Map<String, Object>> verList = objectMapper.readValue(profile.getPhotosVerificationJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                List<String> blockedUrls = new ArrayList<>();
                for (Map<String, Object> item : verList) {
                    Boolean isBlocked = (Boolean) item.get("is_blocked");
                    String status = (String) item.get("status");
                    String pUrl = (String) item.get("photo_url");
                    if ((Boolean.TRUE.equals(isBlocked) || "MISMATCH".equalsIgnoreCase(status)) && pUrl != null && !pUrl.isBlank()) {
                        blockedUrls.add(pUrl.trim());
                    }
                }
                if (!blockedUrls.isEmpty()) {
                    photos.removeIf(p -> blockedUrls.contains(p) || blockedUrls.stream().anyMatch(b -> p.contains(b) || b.contains(p)));
                }
            } catch (Exception ignored) {}
        }

        if (photos.isEmpty()) {
            if (profile != null && profile.getPhoto1() != null && !profile.getPhoto1().isBlank()) {
                photos.add(profile.getPhoto1().trim());
            }
        }

        String photo1 = !photos.isEmpty() ? photos.get(0) : (profile != null ? profile.getPhoto1() : null);
        String photo2 = photos.size() > 1 ? photos.get(1) : (profile != null ? profile.getPhoto2() : null);
        String photo3 = photos.size() > 2 ? photos.get(2) : (profile != null ? profile.getPhoto3() : null);

        String voiceUrl = (profile != null && profile.getVoicePromptUrl() != null && !profile.getVoicePromptUrl().trim().isEmpty())
                ? profile.getVoicePromptUrl().trim() : null;
        if (voiceUrl != null && !voiceUrl.startsWith("http://") && !voiceUrl.startsWith("https://")) {
            voiceUrl = "https://api.blunderr.in/" + voiceUrl.replaceAll("^/+", "");
        }

        String promptText = profile != null ? profile.getVoicePromptText() : null;
        if (promptText == null || promptText.trim().isEmpty()) {
            promptText = "My controversial chai opinion ☕";
        }

        DiscoveryDto.VoicePromptDto voicePromptDto = null;
        if (voiceUrl != null) {
            voicePromptDto = DiscoveryDto.VoicePromptDto.builder()
                    .audioUrl(voiceUrl)
                    .durationSec(profile != null && profile.getVoicePromptDuration() != null ? profile.getVoicePromptDuration() : 15)
                    .promptText(promptText)
                    .build();
        }

        String memeUrl = (profile != null && profile.getSelectedMemeUrl() != null && !profile.getSelectedMemeUrl().trim().isEmpty())
                ? profile.getSelectedMemeUrl().trim() : null;
        String memeTitle = (profile != null && profile.getSelectedMemeTitle() != null && !profile.getSelectedMemeTitle().trim().isEmpty())
                ? profile.getSelectedMemeTitle().trim() : "Profile Meme DNA 🤣";

        if (memeUrl != null && !memeUrl.startsWith("http://") && !memeUrl.startsWith("https://")) {
            memeUrl = "https://api.blunderr.in/" + memeUrl.replaceAll("^/+", "");
        }

        DiscoveryDto.MemeMatchDto memeMatchDto = null;
        if (memeUrl != null) {
            memeMatchDto = DiscoveryDto.MemeMatchDto.builder()
                    .matchPercent(compScore > 0 ? Math.min(99, compScore + 4) : 91)
                    .memeTitle(memeTitle)
                    .memeImageUrl(memeUrl)
                    .build();
        }

        // Live 36-Point Ashtakoot Cosmic Chemistry
        int gunaTotal = 28;
        String synergyTag = "28/36 Cosmic Chemistry ✨";
        String sunSign = (profile != null && profile.getSunSign() != null) ? profile.getSunSign() : null;
        String moonSign = (profile != null && profile.getMoonSign() != null) ? profile.getMoonSign() : null;

        if (viewerId != null && user != null && user.getId() != null) {
            try {
                var astroMatch = astrologyService.calculateAshtakootMatch(viewerId, user.getId());
                gunaTotal = astroMatch.getTotalScore();
                synergyTag = String.format("%d/36 %s", gunaTotal, astroMatch.getVibeTitle());
                if (astroMatch.getCandidate() != null) {
                    if (sunSign == null) sunSign = astroMatch.getCandidate().getSunSign();
                    if (moonSign == null) moonSign = astroMatch.getCandidate().getChandraRashi();
                }
            } catch (Exception e) {
                log.debug("Astro match calculation fallback: {}", e.getMessage());
            }
        }

        // Anti-Stalking Geo-Fuzzy Jitter: bucket exact distances to protect residential privacy
        String locationArea = null;
        if (profile != null) {
            if (profile.getNeighborhood() != null && !profile.getNeighborhood().isBlank()) {
                locationArea = profile.getNeighborhood().trim();
            } else if (profile.getCity() != null && !profile.getCity().isBlank()) {
                locationArea = profile.getCity().trim();
            } else if (profile.getLocation() != null && !profile.getLocation().isBlank()) {
                locationArea = profile.getLocation().trim();
            }
        }

        String distanceTier;
        if (distanceKm < 2.0) {
            distanceTier = "< 2 km";
        } else if (distanceKm < 5.0) {
            distanceTier = "< 5 km";
        } else if (distanceKm < 10.0) {
            distanceTier = "< 10 km";
        } else if (distanceKm < 25.0) {
            distanceTier = "< 25 km";
        } else {
            distanceTier = Math.round(distanceKm) + " km";
        }

        String candCity = (profile != null && profile.getCity() != null && !profile.getCity().isBlank())
                ? profile.getCity().trim() : (locationArea != null ? locationArea : "India");

        String fuzzyDistance;
        if (isSharedCommunity && distanceKm > 50.0) {
            // Member from another city connected via shared micro-community!
            fuzzyDistance = candCity + " · Shared Tribe";
        } else if (isSharedCommunity) {
            fuzzyDistance = (locationArea != null && !locationArea.equalsIgnoreCase("Bengaluru") && !locationArea.equalsIgnoreCase("Bangalore"))
                    ? locationArea + " · " + distanceTier + " · Shared Tribe"
                    : distanceTier + " · Shared Tribe";
        } else {
            fuzzyDistance = (locationArea != null && !locationArea.equalsIgnoreCase("Bengaluru") && !locationArea.equalsIgnoreCase("Bangalore"))
                    ? locationArea + " · " + distanceTier
                    : distanceTier;
        }

        double displayDistanceKm = (isSharedCommunity && distanceKm > 50.0) ? 1.5 : distanceKm;

        return DiscoveryDto.CandidateCardDto.builder()
                .userId(user.getId())
                .displayName(profile != null && profile.getDisplayName() != null ? profile.getDisplayName() : "Single in City")
                .fullName(profile != null && profile.getDisplayName() != null ? profile.getDisplayName() : "Single in City")
                .age(age)
                .isDigilockerVerified(Boolean.TRUE.equals(user.getDigilockerVerified()))
                .isWhatsappVerified(Boolean.TRUE.equals(user.getWhatsappVerified()))
                .livenessScore(user.getLivenessScore() != null ? user.getLivenessScore() : 0.98)
                .distanceKm(displayDistanceKm)
                .fuzzyDistance(fuzzyDistance)
                .isSharedCommunity(isSharedCommunity)
                .culturalBadges(DiscoveryDto.CulturalBadges.builder()
                        .diet(profile != null && profile.getDietaryPref() != null ? profile.getDietaryPref() : DietaryPreference.PURE_VEG)
                        .living(profile != null && profile.getLivingStatus() != null ? profile.getLivingStatus() : LivingStatus.INDEPENDENT_FLAT)
                        .languages(profile != null && profile.getLanguagesSpoken() != null ? profile.getLanguagesSpoken() : List.of("English", "Hindi"))
                        .zodiac(sunSign != null ? sunSign : (profile != null && profile.getZodiacSign() != null ? profile.getZodiacSign() : "Leo"))
                        .build())
                .voicePrompt(voicePromptDto)
                .voicePromptUrl(voiceUrl)
                .selectedMemeUrl(memeUrl)
                .selectedMemeTitle(memeTitle)
                .memeMatch(memeMatchDto)
                .cosmicChemistry(DiscoveryDto.CosmicChemistryDto.builder()
                        .synergyTag(synergyTag)
                        .score(gunaTotal)
                        .build())
                .compatibilityScore(compScore)
                .bio(profile != null && profile.getBio() != null ? profile.getBio() : "")
                .company(profile != null ? profile.getCompany() : null)
                .occupation(profile != null ? profile.getOccupation() : null)
                .job(profile != null ? (profile.getJob() != null ? profile.getJob() : profile.getOccupation()) : null)
                .education(profile != null ? profile.getEducation() : null)
                .institute(profile != null ? profile.getInstitute() : null)
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
                .microCircles(extractUserMicroCircles(profile))
                .photos(photos)
                .photo1(photo1)
                .photo2(photo2)
                .desireMatchPercent(desireMatchPercent)
                .desireMatchHighlights(desireMatchHighlights != null ? desireMatchHighlights : List.of())
                .isFaceVerified(Boolean.TRUE.equals(user.getFaceVerified()))
                .photosVerificationJson(profile != null ? profile.getPhotosVerificationJson() : null)
                .build();
    }

    private boolean isGenderAndInterestMatch(User viewer, Profile viewerProfile, User candidate, Profile candidateProfile) {
        boolean isCandidateFemale = candidate.getGender() == Gender.FEMALE ||
                (candidateProfile != null && candidateProfile.getGenderDisplay() != null &&
                        (candidateProfile.getGenderDisplay().equalsIgnoreCase("Woman") ||
                         candidateProfile.getGenderDisplay().equalsIgnoreCase("Women") ||
                         candidateProfile.getGenderDisplay().equalsIgnoreCase("Female")));

        boolean isCandidateMale = candidate.getGender() == Gender.MALE ||
                (candidateProfile != null && candidateProfile.getGenderDisplay() != null &&
                        (candidateProfile.getGenderDisplay().equalsIgnoreCase("Man") ||
                         candidateProfile.getGenderDisplay().equalsIgnoreCase("Men") ||
                         candidateProfile.getGenderDisplay().equalsIgnoreCase("Male")));

        boolean isViewerFemale = viewer.getGender() == Gender.FEMALE ||
                (viewerProfile != null && viewerProfile.getGenderDisplay() != null &&
                        (viewerProfile.getGenderDisplay().equalsIgnoreCase("Woman") ||
                         viewerProfile.getGenderDisplay().equalsIgnoreCase("Women") ||
                         viewerProfile.getGenderDisplay().equalsIgnoreCase("Female")));

        boolean isViewerMale = viewer.getGender() == Gender.MALE ||
                (viewerProfile != null && viewerProfile.getGenderDisplay() != null &&
                        (viewerProfile.getGenderDisplay().equalsIgnoreCase("Man") ||
                         viewerProfile.getGenderDisplay().equalsIgnoreCase("Men") ||
                         viewerProfile.getGenderDisplay().equalsIgnoreCase("Male")));

        String viewerPref = (viewerProfile != null && viewerProfile.getGenderPreferenceDisplay() != null)
                ? viewerProfile.getGenderPreferenceDisplay().trim().toLowerCase()
                : null;

        // 1. Enforce viewer's explicit gender preference if set
        if ("women".equals(viewerPref) || "woman".equals(viewerPref) || "female".equals(viewerPref)) {
            if (!isCandidateFemale) return false;
        } else if ("men".equals(viewerPref) || "man".equals(viewerPref) || "male".equals(viewerPref)) {
            if (!isCandidateMale) return false;
        } else if ("everyone".equals(viewerPref)) {
            // Allows all genders
        } else {
            // Unset preference: default to opposite gender matching
            if (isViewerMale && !isCandidateFemale) {
                return false;
            }
            if (isViewerFemale && !isCandidateMale) {
                return false;
            }
            if (viewer.getGender() != null && candidate.getGender() != null && viewer.getGender() == candidate.getGender()) {
                return false;
            }
        }

        // 2. Enforce candidate's reciprocal preference (if specified)
        String candidatePref = (candidateProfile != null && candidateProfile.getGenderPreferenceDisplay() != null)
                ? candidateProfile.getGenderPreferenceDisplay().trim().toLowerCase()
                : null;

        if (candidatePref != null) {
            if (("men".equals(candidatePref) || "man".equals(candidatePref) || "male".equals(candidatePref)) && !isViewerMale) {
                return false;
            }
            if (("women".equals(candidatePref) || "woman".equals(candidatePref) || "female".equals(candidatePref)) && !isViewerFemale) {
                return false;
            }
        }

        return true;
    }

    public List<DiscoveryDto.CandidateCardDto> getLikesReceived(UUID targetId) {
        List<Interaction> interactions = interactionRepository.findByTargetIdAndActionTypeInOrderByCreatedAtDesc(
                targetId,
                List.of(ActionType.LIKE, ActionType.SUPER_CHAI, ActionType.SUPER_SPARK)
        );
        if (interactions.isEmpty()) {
            return Collections.emptyList();
        }

        List<DiscoveryDto.CandidateCardDto> result = new ArrayList<>();
        Set<UUID> seenActorIds = new HashSet<>();

        for (Interaction inter : interactions) {
            UUID actorId = inter.getActorId();
            if (actorId == null || !seenActorIds.add(actorId)) {
                continue;
            }

            // If already matched, skip
            Optional<Match> existingMatch = matchRepository.findMatchBetween(actorId, targetId);
            if (existingMatch.isPresent() && existingMatch.get().getStatus() != MatchStatus.UNMATCHED) {
                continue;
            }

            User actor = userRepository.findById(actorId).orElse(null);
            if (actor == null) continue;
            Profile profile = profileRepository.findById(actorId).orElse(null);

            DiscoveryDto.CandidateCardDto card = buildCandidateCard(targetId, actor, profile, 2.5, 95, 95, List.of("Liked Your Profile! ✨"));
            if (inter.getCommentText() != null && !inter.getCommentText().isBlank()) {
                card.setCommentText(inter.getCommentText());
                card.setContextType(inter.getContextType() != null ? inter.getContextType().name() : null);
                card.setContextTargetId(inter.getContextTargetId());
            }
            result.add(card);
        }

        return result;
    }

    public DiscoveryDto.SparkNoteQuotaDto getSparkNoteQuota(UUID actorId) {
        OffsetDateTime startOfDay = OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        long usedToday = interactionRepository.countSparkNotesUsedSince(actorId, startOfDay);
        long remaining = Math.max(0, 1 - usedToday);
        OffsetDateTime tomorrowMidnight = startOfDay.plusDays(1);
        return DiscoveryDto.SparkNoteQuotaDto.builder()
                .totalDaily(1)
                .usedToday(usedToday)
                .remainingToday(remaining)
                .canSendFree(remaining > 0)
                .resetsAt(tomorrowMidnight.toString())
                .build();
    }

    private boolean isSharedCommunityMatch(String commA, String commB) {
        if (commA == null || commB == null) return false;
        String a = commA.trim().toLowerCase();
        String b = commB.trim().toLowerCase();
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equalsIgnoreCase(b)) return true;

        String cleanA = a.replaceAll("[^a-z0-9]", "");
        String cleanB = b.replaceAll("[^a-z0-9]", "");
        if (!cleanA.isEmpty() && !cleanB.isEmpty()) {
            if (cleanA.contains(cleanB) || cleanB.contains(cleanA)) return true;
        }

        // Token intersection check for multi-word communities (e.g. "Cyber Hub")
        Set<String> stopWords = Set.of("and", "the", "&", "club", "circle", "phase", "road", "scene", "collective", "tribe", "founders", "builders");
        String[] tokensA = a.split("[\\s\\-_/&]+");
        String[] tokensB = b.split("[\\s\\-_/&]+");
        int matchCount = 0;
        int meaningfulA = 0;
        for (String tA : tokensA) {
            String tClean = tA.replaceAll("[^a-z0-9]", "");
            if (tClean.length() < 3 || stopWords.contains(tClean)) continue;
            meaningfulA++;
            for (String tB : tokensB) {
                String tbClean = tB.replaceAll("[^a-z0-9]", "");
                if (tbClean.length() < 3 || stopWords.contains(tbClean)) continue;
                if (tClean.equals(tbClean) || tClean.contains(tbClean) || tbClean.contains(tClean)) {
                    matchCount++;
                    break;
                }
            }
        }
        return meaningfulA > 0 && matchCount >= Math.min(2, meaningfulA);
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

    public boolean hasSharedCommunityMatch(List<String> viewerComms, List<String> candidateComms) {
        if (viewerComms == null || viewerComms.isEmpty() || candidateComms == null || candidateComms.isEmpty()) {
            return false;
        }
        for (String v : viewerComms) {
            for (String c : candidateComms) {
                if (isSharedCommunityMatch(v, c)) {
                    return true;
                }
            }
        }
        return false;
    }
}
