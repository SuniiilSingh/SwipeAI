package com.match.SwipeAI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class SystemDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppVersionResponse {
        private String latestVersionName;
        private int latestVersionCode;
        private int minSupportedVersionCode;
        private String updateType; // "FLEXIBLE" or "IMMEDIATE"
        private String title;
        private String message;
        private String apkDownloadUrl;
        private String playStoreUrl;
    }
}
