package com.match.SwipeAI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

public class CallingDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VirtualChaiSessionRequest {
        private UUID matchId;
        private boolean isVideo = false;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VirtualChaiSessionResponse {
        private String roomName;
        private String participantToken;
        private String serverUrl;
        private String callerMaskedName;
        private String recipientMaskedName;
        private boolean phoneMasked;
        private boolean isVideo;
        private boolean isSimulated;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallSignalRequest {
        private UUID matchId;
        private String signalType; // CALL_ACCEPTED, CALL_DECLINED, CALL_ENDED
    }
}
