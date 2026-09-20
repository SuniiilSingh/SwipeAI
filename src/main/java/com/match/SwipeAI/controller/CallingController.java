package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.CallingDto;
import com.match.SwipeAI.model.Match;
import com.match.SwipeAI.model.Profile;
import com.match.SwipeAI.repository.MatchRepository;
import com.match.SwipeAI.repository.ProfileRepository;
import com.match.SwipeAI.service.integration.LiveKitCallingService;
import com.match.SwipeAI.service.integration.PushNotificationService;
import com.match.SwipeAI.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Masked Audio & Video Calling (Virtual Chai) Controller.
 * Provides encrypted WebRTC calling tokens and real-time signaling without exposing phone numbers or WhatsApp contacts.
 */
@Slf4j
@RestController
@RequestMapping("/v1/calling")
@RequiredArgsConstructor
public class CallingController {

    private final LiveKitCallingService liveKitCallingService;
    private final ProfileRepository profileRepository;
    private final MatchRepository matchRepository;
    private final ChatWebSocketHandler webSocketHandler;
    private final PushNotificationService pushNotificationService;

    /**
     * Create a masked "Virtual Chai" WebRTC room session and notify the recipient in real-time.
     *
     * @param userId Authenticated caller UUID
     * @param request Match ID and audio/video mode flag
     * @return LiveKit room name, participant token, and masked caller identity
     */
    @PostMapping("/virtual-chai/session")
    public ResponseEntity<CallingDto.VirtualChaiSessionResponse> createVirtualChaiSession(
            @AuthenticationPrincipal UUID userId,
            @RequestBody CallingDto.VirtualChaiSessionRequest request) {

        Profile callerProfile = profileRepository.findById(userId).orElse(null);
        String callerName = callerProfile != null ? callerProfile.getDisplayName() : "Caller";
        String callerPhoto = callerProfile != null
                ? (callerProfile.getPhoto1() != null ? callerProfile.getPhoto1() : callerProfile.getSelfieUrl())
                : null;

        Match match = matchRepository.findById(request.getMatchId()).orElse(null);
        UUID recipientId = null;
        String recipientName = "Match Partner";

        if (match != null) {
            recipientId = match.getUserAId().equals(userId) ? match.getUserBId() : match.getUserAId();
            Profile recipientProfile = profileRepository.findById(recipientId).orElse(null);
            if (recipientProfile != null && recipientProfile.getDisplayName() != null) {
                recipientName = recipientProfile.getDisplayName();
            }
        }

        // 1. Generate session credentials for caller
        CallingDto.VirtualChaiSessionResponse callerSession = liveKitCallingService.createCallingSession(
                request.getMatchId(),
                userId,
                callerName,
                recipientName,
                request.isVideo()
        );

        // 2. Generate counterpart session and notify recipient in real-time
        if (recipientId != null) {
            CallingDto.VirtualChaiSessionResponse recipientSession = liveKitCallingService.createCallingSession(
                    request.getMatchId(),
                    recipientId,
                    recipientName,
                    callerName,
                    request.isVideo()
            );

            // Broadcast real-time CALL_INCOMING event to recipient over WebSocket
            Map<String, Object> callInvite = new HashMap<>();
            callInvite.put("type", "CALL_INCOMING");
            callInvite.put("matchId", request.getMatchId().toString());
            callInvite.put("callerId", userId.toString());
            callInvite.put("callerName", callerName);
            callInvite.put("callerPhoto", callerPhoto);
            callInvite.put("isVideo", request.isVideo());
            callInvite.put("session", recipientSession);

            webSocketHandler.sendMessageToUser(recipientId.toString(), callInvite);
            log.info("[VIRTUAL CHAI] Dispatched CALL_INCOMING WS event from {} to recipient {}", callerName, recipientId);

            // Send high-priority Push Notification to recipient device
            String callTypeLabel = request.isVideo() ? "Video" : "Audio";
            pushNotificationService.sendChatMessageNotification(
                    recipientId,
                    request.getMatchId(),
                    callerName,
                    "📞 Incoming " + callTypeLabel + " Virtual Chai Date..."
            );
        }

        return ResponseEntity.ok(callerSession);
    }

    /**
     * Send WebRTC call signaling events (CALL_ACCEPTED, CALL_DECLINED, CALL_ENDED)
     */
    @PostMapping("/virtual-chai/signal")
    public ResponseEntity<Map<String, Object>> sendCallSignal(
            @AuthenticationPrincipal UUID userId,
            @RequestBody CallingDto.CallSignalRequest request) {

        Match match = matchRepository.findById(request.getMatchId()).orElse(null);
        if (match != null) {
            UUID recipientId = match.getUserAId().equals(userId) ? match.getUserBId() : match.getUserAId();

            Map<String, Object> signal = new HashMap<>();
            signal.put("type", request.getSignalType());
            signal.put("matchId", request.getMatchId().toString());
            signal.put("senderId", userId.toString());

            webSocketHandler.sendMessageToUser(recipientId.toString(), signal);
            log.info("[VIRTUAL CHAI] Forwarded call signal {} from {} to recipient {}",
                    request.getSignalType(), userId, recipientId);
        }

        return ResponseEntity.ok(Map.of("status", "DELIVERED", "signal", request.getSignalType()));
    }
}
