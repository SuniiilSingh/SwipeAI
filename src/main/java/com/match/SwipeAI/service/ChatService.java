package com.match.SwipeAI.service;

import com.match.SwipeAI.dto.ChatDto;
import com.match.SwipeAI.enums.*;
import com.match.SwipeAI.model.*;
import com.match.SwipeAI.repository.*;
import com.match.SwipeAI.service.engine.MatchKarmaService;
import com.match.SwipeAI.service.integration.NudityDetectorService;
import com.match.SwipeAI.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final MatchRepository matchRepository;
    private final NudityDetectorService nudityDetectorService;
    private final MatchKarmaService karmaService;
    private final ChatWebSocketHandler webSocketHandler;

    public List<ChatDto.ChatMessageResponse> getMessages(UUID matchId, UUID currentUserId) {
        List<ChatMessage> messages = chatMessageRepository.findByMatchIdOrderByCreatedAtAsc(matchId);
        List<ChatDto.ChatMessageResponse> responses = new ArrayList<>();

        for (ChatMessage m : messages) {
            responses.add(ChatDto.ChatMessageResponse.builder()
                    .id(m.getId())
                    .matchId(m.getMatchId())
                    .senderId(m.getSenderId())
                    .recipientId(m.getRecipientId())
                    .content(m.getContent())
                    .mediaUrl(m.getMediaUrl())
                    .mediaType(m.getMediaType())
                    .isBlurred(Boolean.TRUE.equals(m.getIsBlurred()))
                    .blurReason(m.getBlurReason())
                    .createdAt(m.getCreatedAt())
                    .isFromMe(m.getSenderId().equals(currentUserId))
                    .build());
        }

        return responses;
    }

    @Transactional
    public ChatDto.ChatMessageResponse sendMessage(UUID matchId, UUID senderId, ChatDto.SendMessageRequest request) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        if (match.getStatus() == MatchStatus.UNMATCHED || match.getStatus() == MatchStatus.EXPIRED) {
            throw new IllegalStateException("Cannot send message in inactive match.");
        }

        UUID recipientId = match.getUserAId().equals(senderId) ? match.getUserBId() : match.getUserAId();

        boolean isBlurred = false;
        String blurReason = null;

        // Shield 360 AI Safe Detector & Nudity Scan
        if (request.getMediaUrl() != null && !request.getMediaUrl().isBlank()) {
            ChatDto.NudityCheckResponse check = nudityDetectorService.scanMedia(request.getMediaUrl());
            if (check.isSensitive()) {
                isBlurred = true;
                blurReason = "Sensitive Content Warning: Auto-blurred by Shield 360";
            }
        }

        ChatMessage message = ChatMessage.builder()
                .matchId(matchId)
                .senderId(senderId)
                .recipientId(recipientId)
                .content(request.getContent())
                .mediaUrl(request.getMediaUrl())
                .mediaType(request.getMediaType() != null ? request.getMediaType() : MessageType.TEXT)
                .isBlurred(isBlurred)
                .blurReason(blurReason)
                .build();

        message = chatMessageRepository.save(message);

        // Update messages count and reward healthy conversation
        int newCount = (match.getMessagesCount() != null ? match.getMessagesCount() : 0) + 1;
        match.setMessagesCount(newCount);
        matchRepository.save(match);

        if (newCount == 4) {
            // Milestone achieved! Reward +3 karma to both users
            karmaService.rewardActiveConversation(match.getUserAId(), match.getUserBId());
        }

        ChatDto.ChatMessageResponse response = ChatDto.ChatMessageResponse.builder()
                .id(message.getId())
                .matchId(matchId)
                .senderId(senderId)
                .recipientId(recipientId)
                .content(message.getContent())
                .mediaUrl(message.getMediaUrl())
                .mediaType(message.getMediaType())
                .isBlurred(isBlurred)
                .blurReason(blurReason)
                .createdAt(message.getCreatedAt())
                .isFromMe(true)
                .build();

        // Dispatch real-time WebSocket update to recipient
        webSocketHandler.sendMessageToUser(recipientId.toString(), response);

        return response;
    }

    @Transactional
    public void clearMessages(UUID matchId) {
        log.info("Clearing all chat messages for matchId: {}", matchId);
        chatMessageRepository.deleteByMatchId(matchId);
    }
}
