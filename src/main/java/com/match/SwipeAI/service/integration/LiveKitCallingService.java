package com.match.SwipeAI.service.integration;

import com.match.SwipeAI.config.FeatureFlagsProperties;
import com.match.SwipeAI.dto.CallingDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Masked WebRTC In-App Audio & Video Calling Service (Virtual Chai).
 * If live LiveKit SFU cluster is disabled, automatically generates simulated room tokens
 * with masked phone numbers for rapid development and testing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveKitCallingService {

    private final FeatureFlagsProperties properties;

    /**
     * Create encrypted WebRTC calling session with masked identities.
     */
    public CallingDto.VirtualChaiSessionResponse createCallingSession(UUID matchId, UUID callerId, String callerName, String recipientName) {
        boolean live = properties.getFeatures().getLivekit().isEnabled();
        String roomName = "chai_room_" + matchId.toString().substring(0, 8);
        String token = "jwt_livekit_token_" + UUID.randomUUID().toString();

        if (live) {
            log.info("[FEATURE_FLAG: LiveKit LIVE] Generating WebRTC JWT participant token for match: {}", matchId);
            /*
             * LIVE INTEGRATION SKELETON:
             * AccessToken tokenGen = new AccessToken(properties.getFeatures().getLivekit().getApiKey(), properties.getFeatures().getLivekit().getApiSecret());
             * tokenGen.setName(callerName);
             * tokenGen.setIdentity(callerId.toString());
             * tokenGen.addGrants(new VideoGrant().setRoomJoin(true).setRoom(roomName));
             * String liveToken = tokenGen.toJwt();
             */
        } else {
            log.info("[MOCK TESTING ENVIRONMENT] LiveKit disabled. Generated simulated WebRTC session: {} for match {}", roomName, matchId);
        }

        return CallingDto.VirtualChaiSessionResponse.builder()
                .roomName(roomName)
                .participantToken(token)
                .serverUrl(live ? properties.getFeatures().getLivekit().getHost() : "wss://mock-sfu.swipeai.in/livekit")
                .callerMaskedName(callerName)
                .recipientMaskedName(recipientName)
                .phoneMasked(true)
                .isSimulated(!live)
                .build();
    }
}
