package com.match.SwipeAI.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.SwipeAI.dto.MatchDto;
import com.match.SwipeAI.enums.*;
import com.match.SwipeAI.model.*;
import com.match.SwipeAI.repository.*;
import com.match.SwipeAI.service.engine.IcebreakerEngine;
import com.match.SwipeAI.service.engine.MatchKarmaService;
import com.match.SwipeAI.service.integration.AiWingmanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.*;

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
    private final MatchKarmaService karmaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<MatchDto.MatchResponseDto> getMatchesForUser(UUID userId) {
        List<Match> matches = matchRepository.findActiveMatchesForUser(userId);
        List<MatchDto.MatchResponseDto> result = new ArrayList<>();

        for (Match match : matches) {
            UUID otherUserId = match.getUserAId().equals(userId) ? match.getUserBId() : match.getUserAId();
            User otherUser = userRepository.findById(otherUserId).orElse(null);
            Profile otherProfile = profileRepository.findById(otherUserId).orElse(null);

            if (otherUser != null) {
                result.add(mapToMatchDto(match, userId, otherUser, otherProfile));
            }
        }

        return result;
    }

    public MatchDto.MatchResponseDto getMatchDetails(UUID matchId, UUID userId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

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
        List<String> sparks = aiWingmanService.generateConversationSparks(myProfile, otherProfile);

        return MatchDto.IcebreakerAnswerResponse.builder()
                .isQuizCompleted(quiz.isCompleted())
                .isMutualAgreement(quiz.isMutualAgreement())
                .newMatchStatus(match.getStatus())
                .quizState(quiz)
                .wingmanSparks(sparks)
                .message(quiz.isCompleted() ? "Chat lounge unlocked! AI Wingman sparks generated below." : "Answer recorded! Waiting for match to respond.")
                .build();
    }

    public MatchDto.WingmanSparksResponse getWingmanSparks(UUID matchId, UUID userId) {
        Match match = matchRepository.findById(matchId).orElseThrow();
        UUID otherUserId = match.getUserAId().equals(userId) ? match.getUserBId() : match.getUserAId();

        Profile myProfile = profileRepository.findById(userId).orElse(null);
        Profile otherProfile = profileRepository.findById(otherUserId).orElse(null);

        List<String> sparks = aiWingmanService.generateConversationSparks(myProfile, otherProfile);

        return MatchDto.WingmanSparksResponse.builder()
                .matchId(matchId)
                .candidateName(otherProfile != null ? otherProfile.getDisplayName() : "Match")
                .sparks(sparks)
                .commonGround("Both love Indiranagar specialty coffee & indie playlists")
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

        int age = 24;
        if (otherUser.getBirthDate() != null) {
            age = Period.between(otherUser.getBirthDate(), LocalDate.now()).getYears();
        }

        String photo = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500";
        if (otherProfile != null && otherProfile.getPhotosJson() != null) {
            try {
                List<String> photos = objectMapper.readValue(otherProfile.getPhotosJson(), new TypeReference<>() {});
                if (!photos.isEmpty()) photo = photos.get(0);
            } catch (Exception ignored) {}
        }

        List<ChatMessage> messages = chatMessageRepository.findByMatchIdOrderByCreatedAtAsc(match.getId());
        String lastMsg = null;
        OffsetDateTime lastTime = null;
        if (!messages.isEmpty()) {
            ChatMessage latest = messages.get(messages.size() - 1);
            lastMsg = latest.getContent();
            lastTime = latest.getCreatedAt();
        }

        return MatchDto.MatchResponseDto.builder()
                .id(match.getId())
                .otherUserId(otherUser.getId())
                .otherUserName(otherProfile != null ? otherProfile.getDisplayName() : "Single")
                .otherUserPhoto(photo)
                .otherUserAge(age)
                .isDigilockerVerified(Boolean.TRUE.equals(otherUser.getDigilockerVerified()))
                .status(match.getStatus())
                .messagesCount(match.getMessagesCount())
                .remainingHours(remainingHours)
                .expiresAt(match.getExpiresAt())
                .matchedAt(match.getMatchedAt())
                .icebreakerQuiz(icebreakerEngine.parseQuizData(match.getIcebreakerGameData()))
                .lastMessage(lastMsg)
                .lastMessageTime(lastTime)
                .build();
    }
}
