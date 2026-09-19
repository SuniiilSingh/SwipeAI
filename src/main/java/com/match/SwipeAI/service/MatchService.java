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
        List<Match> matches = matchRepository.findActiveMatchesForUser(userId);
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
        match.setStatus(MatchStatus.UNMATCHED);
        matchRepository.save(match);
        log.info("User {} unmatched match {}", userId, matchId);
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

        List<ChatMessage> messages = chatMessageRepository.findByMatchIdOrderByCreatedAtAsc(match.getId());
        String lastMsg = null;
        OffsetDateTime lastTime = null;
        if (!messages.isEmpty()) {
            ChatMessage latest = messages.get(messages.size() - 1);
            if (latest.getContent() != null && latest.getContent().startsWith("E2EE:v1:")) {
                lastMsg = "🔒 Encrypted message";
            } else {
                lastMsg = com.match.SwipeAI.service.engine.ChatCryptoService.getInstance().decrypt(latest.getContent());
            }
            lastTime = latest.getCreatedAt();
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
                .icebreakerQuiz(icebreakerEngine.parseQuizData(match.getIcebreakerGameData()))
                .lastMessage(lastMsg)
                .lastMessageTime(lastTime)
                .otherProfile(candidateCard)
                .build();
    }
}
