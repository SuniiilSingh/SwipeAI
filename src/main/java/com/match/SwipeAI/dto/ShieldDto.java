package com.match.SwipeAI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class ShieldDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncContactsRequest {
        private List<String> contactHashes; // SHA-256 salted hashes computed client-side
        private List<String> rawPhoneNumbers; // Optional: client can send raw phones and server salts & hashes with server pepper
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DomainShieldRequest {
        private String corporateDomain; // e.g. "swiggy.in", "tcs.com", "google.com"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShieldStatusResponse {
        private int shieldedContactsCount;
        private String corporateDomain;
        private boolean isShieldActive;
        private String message;
    }
}
