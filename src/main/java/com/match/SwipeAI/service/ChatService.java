package com.match.SwipeAI.service;

import com.match.SwipeAI.dto.ChatDto;
import com.match.SwipeAI.enums.*;
import com.match.SwipeAI.model.*;
import com.match.SwipeAI.repository.*;
import com.match.SwipeAI.service.engine.MatchKarmaService;
import com.match.SwipeAI.service.integration.NudityDetectorService;
import com.match.SwipeAI.service.integration.PushNotificationService;
import com.match.SwipeAI.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final MatchRepository matchRepository;
    private final ProfileRepository profileRepository;
    private final NudityDetectorService nudityDetectorService;
    private final MatchKarmaService karmaService;
    private final ChatWebSocketHandler webSocketHandler;
    private final PushNotificationService pushNotificationService;

    @Transactional
    public List<ChatDto.ChatMessageResponse> getMessages(UUID matchId, UUID currentUserId) {
        return getMessages(matchId, currentUserId, org.springframework.data.domain.PageRequest.of(0, 100));
    }

    @Transactional
    public List<ChatDto.ChatMessageResponse> getMessages(UUID matchId, UUID currentUserId, org.springframework.data.domain.Pageable pageable) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        // Strict Match Lounge Authorization: Only authenticated participants may decrypt & view messages
        if (!match.getUserAId().equals(currentUserId) && !match.getUserBId().equals(currentUserId)) {
            throw new AccessDeniedException("Access denied: You are not an authorized participant in this encrypted chat lounge.");
        }

        List<ChatMessage> messages = chatMessageRepository.findByMatchIdOrderByCreatedAtAsc(matchId, pageable);
        List<ChatDto.ChatMessageResponse> responses = new ArrayList<>();
        List<ChatMessage> toMarkRead = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (ChatMessage m : messages) {
            // Update unread messages for recipient
            if (m.getRecipientId().equals(currentUserId) && m.getStatus() != MessageStatus.READ) {
                m.setStatus(MessageStatus.READ);
                m.setReadAt(now);
                toMarkRead.add(m);
            }

            responses.add(ChatDto.ChatMessageResponse.builder()
                    .id(m.getId())
                    .matchId(m.getMatchId())
                    .senderId(m.getSenderId())
                    .recipientId(m.getRecipientId())
                    .content(m.getContent())
                    .mediaUrl(m.getMediaUrl())
                    .mediaType(m.getMediaType())
                    .status(m.getStatus())
                    .isEncrypted(Boolean.TRUE.equals(m.getIsEncrypted()))
                    .encryptionAlgo(m.getEncryptionAlgo() != null ? m.getEncryptionAlgo() : "AES-256-GCM")
                    .isBlurred(Boolean.TRUE.equals(m.getIsBlurred()))
                    .blurReason(m.getBlurReason())
                    .readAt(m.getReadAt())
                    .createdAt(m.getCreatedAt())
                    .isFromMe(m.getSenderId().equals(currentUserId))
                    .build());
        }

        if (!toMarkRead.isEmpty()) {
            chatMessageRepository.saveAll(toMarkRead);
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

        // Strict Match Lounge Authorization
        if (!match.getUserAId().equals(senderId) && !match.getUserBId().equals(senderId)) {
            throw new AccessDeniedException("Access denied: You are not authorized to send messages in this lounge.");
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

        boolean isE2ee = request.getContent() != null && request.getContent().startsWith("E2EE:");
        String algo = isE2ee ? "AES-256-GCM-E2EE" : "AES-256-GCM";

        ChatMessage message = ChatMessage.builder()
                .matchId(matchId)
                .senderId(senderId)
                .recipientId(recipientId)
                .content(request.getContent())
                .mediaUrl(request.getMediaUrl())
                .mediaType(request.getMediaType() != null ? request.getMediaType() : MessageType.TEXT)
                .status(MessageStatus.SENT)
                .isEncrypted(true)
                .encryptionAlgo(algo)
                .isBlurred(isBlurred)
                .blurReason(blurReason)
                .expiresAt(match.getExpiresAt())
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
                .status(message.getStatus())
                .isEncrypted(true)
                .encryptionAlgo(algo)
                .isBlurred(isBlurred)
                .blurReason(blurReason)
                .readAt(message.getReadAt())
                .createdAt(message.getCreatedAt())
                .isFromMe(true)
                .build();

        // Dispatch real-time WebSocket update to recipient (with isFromMe = false for the recipient)
        ChatDto.ChatMessageResponse recipientResponse = ChatDto.ChatMessageResponse.builder()
                .id(message.getId())
                .matchId(matchId)
                .senderId(senderId)
                .recipientId(recipientId)
                .content(message.getContent())
                .mediaUrl(message.getMediaUrl())
                .mediaType(message.getMediaType())
                .status(message.getStatus())
                .isEncrypted(true)
                .encryptionAlgo(algo)
                .isBlurred(isBlurred)
                .blurReason(blurReason)
                .readAt(message.getReadAt())
                .createdAt(message.getCreatedAt())
                .isFromMe(false)
                .build();
        webSocketHandler.sendMessageToUser(recipientId.toString(), recipientResponse);

        // Dispatch background push notification to recipient
        String senderName = profileRepository.findById(senderId).map(Profile::getDisplayName).orElse("Your match");
        String snippet = request.getContent() != null && !request.getContent().isBlank() ? request.getContent() : "Sent an attachment";
        if (isE2ee) {
            snippet = "Sent you an end-to-end encrypted message 🔒";
        }
        pushNotificationService.sendChatMessageNotification(recipientId, matchId, senderName, snippet);

        return response;
    }

    @Transactional
    public void markMessagesAsRead(UUID matchId, UUID currentUserId) {
        List<ChatMessage> messages = chatMessageRepository.findByMatchIdOrderByCreatedAtAsc(matchId);
        List<ChatMessage> toMark = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();
        for (ChatMessage m : messages) {
            if (m.getRecipientId().equals(currentUserId) && m.getStatus() != MessageStatus.READ) {
                m.setStatus(MessageStatus.READ);
                m.setReadAt(now);
                toMark.add(m);
            }
        }
        if (!toMark.isEmpty()) {
            chatMessageRepository.saveAll(toMark);
        }
    }

    @Transactional
    public void clearMessages(UUID matchId) {
        log.info("Clearing all chat messages for matchId: {}", matchId);
        chatMessageRepository.deleteByMatchId(matchId);
    }
}
