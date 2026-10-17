package com.match.SwipeAI.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.SwipeAI.dto.DiscoveryDto;
import com.match.SwipeAI.dto.MatchDto;
import com.match.SwipeAI.enums.*;
import com.match.SwipeAI.model.*;
import com.match.SwipeAI.repository.*;
import com.match.SwipeAI.service.engine.IcebreakerEngine;
import com.match.SwipeAI.service.engine.MatchKarmaService;
import com.match.SwipeAI.service.engine.MutualChemistrySparksEngine;
import com.match.SwipeAI.service.integration.AiWingmanService;
import com.match.SwipeAI.service.integration.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final IcebreakerEngine icebreakerEngine;
    private final AiWingmanService aiWingmanService;
    private final MutualChemistrySparksEngine mutualChemistrySparksEngine;
    private final MatchKarmaService karmaService;
    private final DiscoveryService discoveryService;
    private final PushNotificationService pushNotificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<MatchDto.MatchResponseDto> getMatchesForUser(UUID userId) {
        return getMatchesForUser(userId, PageRequest.of(0, 50));
    }

    public List<MatchDto.MatchResponseDto> getMatchesForUser(UUID userId, Pageable pageable) {
        List<Match> matches = matchRepository.findActiveMatchesForUser(userId, pageable);
        if (matches.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> otherUserIds = matches.stream()
                .map(m -> m.getUserAId().equals(userId) ? m.getUserBId() : m.getUserAId())
                .distinct()
                .toList();

        Map<UUID, User> userMap = userRepository.findAllById(otherUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, Profile> profileMap = profileRepository.findAllById(otherUserIds).stream()
                .collect(Collectors.toMap(Profile::getUserId, p -> p));

        List<MatchDto.MatchResponseDto> result = new ArrayList<>(matches.size());
        for (Match match : matches) {
            UUID otherUserId = match.getUserAId().equals(userId) ? match.getUserBId() : match.getUserAId();
            User otherUser = userMap.get(otherUserId);
            Profile otherProfile = profileMap.get(otherUserId);

            if (otherUser != null) {
                result.add(mapToMatchDto(match, userId, otherUser, otherProfile));
            }
        }

        return result;
    }

    public MatchDto.MatchResponseDto getMatchDetails(UUID matchId, UUID userId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        if (!match.getUserAId().equals(userId) && !match.getUserBId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: You are not an authorized participant in this match.");
        }

        UUID otherUserId = match.getUserAId().equals(userId) ? match.getUserBId() : match.getUserAId();
        User otherUser = userRepository.findById(otherUserId).orElseThrow();
        Profile otherProfile = profileRepository.findById(otherUserId).orElse(null);

        return mapToMatchDto(match, userId, otherUser, otherProfile);
    }

    @Transactional
    public MatchDto.IcebreakerAnswerResponse answerIcebreaker(UUID matchId, UUID userId, int optionIndex) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        MatchDto.IcebreakerQuizDto quiz = icebreakerEngine.parseQuizData(match.getIcebreakerGameData());

        boolean isUserA = match.getUserAId().equals(userId);
        if (isUserA) {
            quiz.setUserAAnswer(optionIndex);
        } else {
            quiz.setUserBAnswer(optionIndex);
        }

        // Check if both answered or if this is the final answer
        if (quiz.getUserAAnswer() != null && quiz.getUserBAnswer() != null) {
            quiz.setCompleted(true);
            quiz.setMutualAgreement(Objects.equals(quiz.getUserAAnswer(), quiz.getUserBAnswer()));
            match.setStatus(MatchStatus.ACTIVE_CHAT);
            match.setExpiresAt(OffsetDateTime.now().plusHours(48)); // Fresh 48h active chat timer
            log.info("Icebreaker completed for match {}. Unlocking ACTIVE_CHAT lounge!", matchId);
        }

        match.setIcebreakerGameData(icebreakerEngine.serializeQuizData(quiz));
        matchRepository.save(match);

        // Fetch AI Wingman conversational sparks
        UUID otherUserId = isUserA ? match.getUserBId() : match.getUserAId();
        Profile myProfile = profileRepository.findById(userId).orElse(null);
        Profile otherProfile = profileRepository.findById(otherUserId).orElse(null);
        // === AI WINGMAN (COMMENTED OUT AS OF NOW) ===
        // List<String> sparks = aiWingmanService.generateConversationSparks(myProfile, otherProfile);

        // === ALTERNATIVE 1: MUTUAL CHEMISTRY SPARKS ENGINE ===
        List<String> sparks = mutualChemistrySparksEngine.generateMutualSparks(myProfile, otherProfile, quiz);

        if (quiz.isCompleted()) {
            String partnerName = (myProfile != null && myProfile.getDisplayName() != null) ? myProfile.getDisplayName() : "Your match";
            pushNotificationService.sendChatUnlockedNotification(otherUserId, matchId, partnerName);
        }

        return MatchDto.IcebreakerAnswerResponse.builder()
                .isQuizCompleted(quiz.isCompleted())
                .isMutualAgreement(quiz.isMutualAgreement())
                .newMatchStatus(match.getStatus())
                .quizState(quiz)
                .wingmanSparks(sparks)
                .message(quiz.isCompleted() ? "Chat lounge unlocked! Mutual Chemistry sparks generated below." : "Answer recorded! Waiting for match to respond.")
                .build();
    }

    public MatchDto.WingmanSparksResponse getWingmanSparks(UUID matchId, UUID userId) {
        Match match = matchRepository.findById(matchId).orElseThrow();
        UUID otherUserId = match.getUserAId().equals(userId) ? match.getUserBId() : match.getUserAId();

        Profile myProfile = profileRepository.findById(userId).orElse(null);
        Profile otherProfile = profileRepository.findById(otherUserId).orElse(null);

        MatchDto.IcebreakerQuizDto quiz = icebreakerEngine.parseQuizData(match.getIcebreakerGameData());

        // === AI WINGMAN (COMMENTED OUT AS OF NOW) ===
        // List<String> sparks = aiWingmanService.generateConversationSparks(myProfile, otherProfile);

        // === ALTERNATIVE 1: MUTUAL CHEMISTRY SPARKS ENGINE ===
        List<String> sparks = mutualChemistrySparksEngine.generateMutualSparks(myProfile, otherProfile, quiz);

        List<String> commonInterests = mutualChemistrySparksEngine.findCommonInterests(myProfile, otherProfile);
        String commonGround = !commonInterests.isEmpty()
                ? "Shared Interests: " + String.join(", ", commonInterests)
                : "Mutual Match on Lifestyle & Intent Preferences";

        return MatchDto.WingmanSparksResponse.builder()
                .matchId(matchId)
                .candidateName(otherProfile != null ? otherProfile.getDisplayName() : "Match")
                .sparks(sparks)
                .commonGround(commonGround)
                .build();
    }

    @Transactional
    public void unmatch(UUID matchId, UUID userId) {
        Match match = matchRepository.findById(matchId).orElseThrow();
        UUID otherUserId = match.getUserAId().equals(userId) ? match.getUserBId() : match.getUserAId();
        match.setStatus(MatchStatus.UNMATCHED);
        matchRepository.save(match);
        log.info("User {} unmatched match {}", userId, matchId);

        promoteQueuedMatchesIfSlotsOpen(userId);
        promoteQueuedMatchesIfSlotsOpen(otherUserId);
    }

    public MatchDto.MatchCapacityDto getMatchCapacity(UUID userId) {
        long activeCount = matchRepository.countActiveRuleOfFiveMatches(userId);
        long queuedCount = matchRepository.findQueuedMatchesForUser(userId).size();
        long plannedCount = matchRepository.findPlannedDatesForUser(userId).size();
        boolean isAtCapacity = activeCount >= 5;

        String msg;
        if (isAtCapacity) {
            msg = "Your inbox is at full focus (5/5). Resolve a conversation to unlock new connections.";
        } else {
            long openSlots = 5 - activeCount;
            msg = openSlots + (openSlots == 1 ? " slot open" : " slots open") + " for high-intent connections.";
        }

        return MatchDto.MatchCapacityDto.builder()
                .activeMatchesCount(activeCount)
                .maxLimit(5)
                .isAtCapacity(isAtCapacity)
                .queuedMatchesCount(queuedCount)
                .plannedDatesCount(plannedCount)
                .message(msg)
                .build();
    }

    @Transactional
    public MatchDto.MatchResponseDto markDatePlanned(UUID matchId, UUID userId, String venueName, String note) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        if (!match.getUserAId().equals(userId) && !match.getUserBId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Not authorized");
        }

        match.setStatus(MatchStatus.DATE_PLANNED);
        match = matchRepository.save(match);
        log.info("Match {} marked as DATE_PLANNED by user {}. Active slot freed under Rule of 5!", matchId, userId);

        UUID otherUserId = match.getUserAId().equals(userId) ? match.getUserBId() : match.getUserAId();
        User otherUser = userRepository.findById(otherUserId).orElseThrow();
        Profile otherProfile = profileRepository.findById(otherUserId).orElse(null);

        // Notify both users
        pushNotificationService.sendMatchNotification(userId, otherUserId, matchId, "Date Planned! 🎉", "Date Planned! 🎉");

        // Promote queued matches for both users
        promoteQueuedMatchesIfSlotsOpen(userId);
        promoteQueuedMatchesIfSlotsOpen(otherUserId);

        return mapToMatchDto(match, userId, otherUser, otherProfile);
    }

    @Transactional
    public void gracefulExit(UUID matchId, UUID userId, String farewellNote) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        if (!match.getUserAId().equals(userId) && !match.getUserBId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Not authorized");
        }

        UUID otherUserId = match.getUserAId().equals(userId) ? match.getUserBId() : match.getUserAId();
        match.setStatus(MatchStatus.UNMATCHED);
        matchRepository.save(match);
        log.info("User {} performed graceful exit on match {}", userId, matchId);

        promoteQueuedMatchesIfSlotsOpen(userId);
        promoteQueuedMatchesIfSlotsOpen(otherUserId);
    }

    @Transactional
    public void promoteQueuedMatchesIfSlotsOpen(UUID userId) {
        long active = matchRepository.countActiveRuleOfFiveMatches(userId);
        if (active < 5) {
            List<Match> queued = matchRepository.findQueuedMatchesForUser(userId);
            for (Match m : queued) {
                UUID partnerId = m.getUserAId().equals(userId) ? m.getUserBId() : m.getUserAId();
                if (matchRepository.countActiveRuleOfFiveMatches(partnerId) < 5) {
                    m.setStatus(MatchStatus.PENDING_ICEBREAKER);
                    m.setExpiresAt(OffsetDateTime.now().plusHours(72));
                    matchRepository.save(m);
                    log.info("Promoted queued match {} between {} and {} into active lounge!", m.getId(), userId, partnerId);
                    pushNotificationService.sendMatchNotification(userId, partnerId, m.getId(), "A queued connection", "A queued connection");
                    active++;
                    if (active >= 5) break;
                }
            }
        }
    }

    @Transactional
    public void reportMatch(UUID matchId, UUID userId, String reason) {
        Match match = matchRepository.findById(matchId).orElseThrow();
        match.setStatus(MatchStatus.UNMATCHED);
        matchRepository.save(match);

        UUID reportedUserId = match.getUserAId().equals(userId) ? match.getUserBId() : match.getUserAId();
        karmaService.penaltyHarassment(reportedUserId);
        log.warn("User {} reported match {} with user {} for reason: '{}'. Applied -50 karma penalty.",
                userId, matchId, reportedUserId, reason);
    }

    private MatchDto.MatchResponseDto mapToMatchDto(Match match, UUID currentUserId, User otherUser, Profile otherProfile) {
        long remainingHours = Math.max(0, Duration.between(OffsetDateTime.now(), match.getExpiresAt()).toHours());

        if (otherProfile == null) {
            otherProfile = Profile.builder()
                    .userId(otherUser.getId())
                    .displayName(otherUser.getPhoneE164() != null ? "User" : "Single in City")
                    .languagesSpoken(List.of("English", "Hindi"))
                    .photosJson("[]")
                    .build();
        }

        DiscoveryDto.CandidateCardDto candidateCard = discoveryService.buildCandidateCard(otherUser, otherProfile, 3.5, 92);

        int age = candidateCard.getAge() > 0 ? candidateCard.getAge() : 24;
        if (otherUser.getBirthDate() != null) {
            age = Period.between(otherUser.getBirthDate(), LocalDate.now()).getYears();
        }

        String photo = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500";
        if (candidateCard.getPhotos() != null && !candidateCard.getPhotos().isEmpty()) {
            photo = candidateCard.getPhotos().get(0);
        }

        if (match.getE2eeSecret() == null) {
            match.setE2eeSecret(UUID.randomUUID().toString());
            matchRepository.save(match);
        }

        Optional<ChatMessage> latestOpt = chatMessageRepository.findFirstByMatchIdOrderByCreatedAtDesc(match.getId());
        String lastMsg = null;
        OffsetDateTime lastTime = null;
        if (latestOpt.isPresent()) {
            ChatMessage latest = latestOpt.get();
            if (latest.getContent() != null && latest.getContent().startsWith("E2EE:v1:")) {
                lastMsg = "🔒 Encrypted message";
            } else {
                lastMsg = com.match.SwipeAI.service.engine.ChatCryptoService.getInstance().decrypt(latest.getContent());
            }
            lastTime = latest.getCreatedAt();
        }

        MatchDto.IcebreakerQuizDto quizDto = icebreakerEngine.parseQuizData(match.getIcebreakerGameData());
        if (quizDto != null) {
            boolean isUserA = match.getUserAId().equals(currentUserId);
            boolean answered = isUserA ? (quizDto.getUserAAnswer() != null) : (quizDto.getUserBAnswer() != null);
            quizDto.setHasAnswered(answered || quizDto.isCompleted());
        }

        return MatchDto.MatchResponseDto.builder()
                .id(match.getId())
                .otherUserId(otherUser.getId())
                .otherUserName(candidateCard.getDisplayName() != null ? candidateCard.getDisplayName() : "Single")
                .otherUserPhoto(photo)
                .otherUserAge(age)
                .isDigilockerVerified(candidateCard.isDigilockerVerified())
                .status(match.getStatus())
                .messagesCount(match.getMessagesCount())
                .remainingHours(remainingHours)
                .expiresAt(match.getExpiresAt())
                .matchedAt(match.getMatchedAt())
                .e2eeSecret(match.getE2eeSecret())
                .icebreakerQuiz(quizDto)
                .lastMessage(lastMsg)
                .lastMessageTime(lastTime)
                .otherProfile(candidateCard)
                .build();
    }

    /**
     * 6-Hour Background Expiry Sweep:
     * Finds active chats whose 48-hour countdown has elapsed and marks them EXPIRED.
     * Note: We intentionally do NOT auto-delete or auto-evict them; they remain in
     * the user's inbox marked as EXPIRED so the user can consciously decide whether
     * to Plan a Date or Farewell them when freeing a slot under the Rule of 5.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 6 * 60 * 60 * 1000)
    @Transactional
    public void processExpiredMatches() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Match> expiredMatches = matchRepository.findExpiredActiveMatches(now);
        if (!expiredMatches.isEmpty()) {
            log.info("Processing {} expired matches in 6-hour scheduled job", expiredMatches.size());
            for (Match m : expiredMatches) {
                m.setStatus(MatchStatus.EXPIRED);
                matchRepository.save(m);
            }
        }
    }

    /**
     * Unlock a specific queued match when an active slot opens up under the Rule of 5.
     */
    @Transactional
    public MatchDto.MatchResponseDto unlockSpecificQueuedMatch(UUID matchId, UUID userId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        if (!match.getUserAId().equals(userId) && !match.getUserBId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Not authorized to unlock this match");
        }

        long active = matchRepository.countActiveRuleOfFiveMatches(userId);
        if (active >= 5) {
            throw new IllegalStateException("Cannot unlock: user is already at maximum 5 active matches");
        }

        match.setStatus(MatchStatus.PENDING_ICEBREAKER);
        match.setExpiresAt(OffsetDateTime.now().plusHours(48));
        match = matchRepository.save(match);
        log.info("User {} specifically unlocked queued match {}", userId, matchId);

        UUID otherUserId = match.getUserAId().equals(userId) ? match.getUserBId() : match.getUserAId();
        User otherUser = userRepository.findById(otherUserId).orElseThrow();
        Profile otherProfile = profileRepository.findById(otherUserId).orElse(null);

        return mapToMatchDto(match, userId, otherUser, otherProfile);
    }
}

