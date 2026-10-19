package com.match.SwipeAI.service.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.match.SwipeAI.config.FeatureFlagsProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceMatchService {

    private final FeatureFlagsProperties properties;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FaceMatchResult {
        private boolean match;
        private double similarityScore;
        private double confidencePercent;
        private int selfieFacesDetected;
        private int photoFacesDetected;
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PythonServiceResponse {
        private String status;
        @JsonProperty("is_match")
        private boolean isMatch;
        @JsonProperty("similarity_score")
        private double similarityScore;
        @JsonProperty("confidence_percent")
        private double confidencePercent;
        @JsonProperty("selfie_faces_detected")
        private int selfieFacesDetected;
        @JsonProperty("photo_faces_detected")
        private int photoFacesDetected;
        private String message;
    }

    public FaceMatchResult compareFaces(String selfieBase64, String referencePhotoPathOrUrl) {
        FeatureFlagsProperties.FaceMatch config = properties.getFeatures().getFacematch();
        if (config == null || !config.isEnabled()) {
            log.info("FaceMatch feature is disabled in config. Simulating pass.");
            return FaceMatchResult.builder()
                    .match(true)
                    .similarityScore(0.92)
                    .confidencePercent(92.0)
                    .selfieFacesDetected(1)
                    .photoFacesDetected(1)
                    .status("SUCCESS")
                    .message("Biometric face match verified (simulated).")
                    .build();
        }

        String serviceUrl = config.getServiceUrl();
        if (serviceUrl == null || serviceUrl.trim().isEmpty()) {
            serviceUrl = "http://facematch:5000";
        }
        String endpoint = serviceUrl.replaceAll("/+$", "") + "/match-faces";

        log.info("Dispatching biometric 1:1 face match to self-hosted service: {}", endpoint);

        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(8000);
            factory.setReadTimeout(15000);
            RestTemplate restTemplate = new RestTemplate(factory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("selfie_base64", selfieBase64);
            body.put("photo_url", referencePhotoPathOrUrl);
            body.put("threshold", config.getThreshold());

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<PythonServiceResponse> response = restTemplate.postForEntity(
                    endpoint,
                    requestEntity,
                    PythonServiceResponse.class
            );

            PythonServiceResponse resBody = response.getBody();
            if (resBody == null) {
                throw new IllegalStateException("Empty response from facematch service");
            }

            log.info("Face match response: match={}, score={}, status={}, msg={}",
                    resBody.isMatch(), resBody.getSimilarityScore(), resBody.getStatus(), resBody.getMessage());

            return FaceMatchResult.builder()
                    .match(resBody.isMatch())
                    .similarityScore(resBody.getSimilarityScore())
                    .confidencePercent(resBody.getConfidencePercent())
                    .selfieFacesDetected(resBody.getSelfieFacesDetected())
                    .photoFacesDetected(resBody.getPhotoFacesDetected())
                    .status(resBody.getStatus())
                    .message(resBody.getMessage())
                    .build();

        } catch (Exception e) {
            log.error("Failed to connect to self-hosted FaceMatch microservice at {}: {}", endpoint, e.getMessage());
            // Fallback for dev/staging if container is offline: return informative failure
            return FaceMatchResult.builder()
                    .match(false)
                    .similarityScore(0.0)
                    .confidencePercent(0.0)
                    .selfieFacesDetected(0)
                    .photoFacesDetected(0)
                    .status("SERVICE_UNAVAILABLE")
                    .message("Face verification engine is temporarily busy. Please try again in a few moments.")
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LivenessMotionResult {
        private boolean liveHuman;
        private double livenessScore;
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PythonLivenessResponse {
        private String status;
        @JsonProperty("is_live_human")
        private boolean isLiveHuman;
        @JsonProperty("liveness_score")
        private double livenessScore;
        private String message;
    }

    public LivenessMotionResult verifyLivenessMotion(String centerBase64, String rightBase64, String leftBase64) {
        return verifyLivenessMotion(centerBase64, rightBase64, leftBase64, null, "TURN");
    }

    public LivenessMotionResult verifyLivenessMotion(
            String centerBase64,
            String rightBase64,
            String leftBase64,
            String actionBase64,
            String actionType
    ) {
        FeatureFlagsProperties.FaceMatch config = properties.getFeatures().getFacematch();
        if (config == null || !config.isEnabled()) {
            return LivenessMotionResult.builder()
                    .liveHuman(true)
                    .livenessScore(0.98)
                    .status("VERIFIED")
                    .message("3D Biometric Liveness verified.")
                    .build();
        }

        String serviceUrl = config.getServiceUrl();
        if (serviceUrl == null || serviceUrl.trim().isEmpty()) {
            serviceUrl = "http://facematch:5000";
        }
        String endpoint = serviceUrl.replaceAll("/+$", "") + "/verify-liveness";

        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(8000);
            factory.setReadTimeout(15000);
            RestTemplate restTemplate = new RestTemplate(factory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("center_base64", centerBase64);
            if (rightBase64 != null) body.put("right_base64", rightBase64);
            if (leftBase64 != null) body.put("left_base64", leftBase64);
            if (actionBase64 != null) body.put("action_base64", actionBase64);
            if (actionType != null) body.put("action_type", actionType);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<PythonLivenessResponse> response = restTemplate.postForEntity(
                    endpoint,
                    requestEntity,
                    PythonLivenessResponse.class
            );

            PythonLivenessResponse resBody = response.getBody();
            if (resBody == null) {
                throw new IllegalStateException("Empty response from facematch /verify-liveness");
            }

            log.info("3D Liveness motion response: live={}, score={}, status={}, msg={}",
                    resBody.isLiveHuman(), resBody.getLivenessScore(), resBody.getStatus(), resBody.getMessage());

            return LivenessMotionResult.builder()
                    .liveHuman(resBody.isLiveHuman())
                    .livenessScore(resBody.getLivenessScore())
                    .status(resBody.getStatus())
                    .message(resBody.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Failed to connect to FaceMatch /verify-liveness at {}: {}", endpoint, e.getMessage());
            return LivenessMotionResult.builder()
                    .liveHuman(false)
                    .livenessScore(0.35)
                    .status("SERVICE_UNAVAILABLE")
                    .message("3D Liveness engine is temporarily busy. Please try again.")
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhotoSlotItemDto {
        private int slot;
        @JsonProperty("photo_base64")
        private String photoBase64;
        @JsonProperty("photo_url")
        private String photoUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PhotoSlotVerificationResultDto {
        private int slot;
        @JsonProperty("photo_url")
        private String photoUrl;
        private String status; // "MATCHED", "GROUP_MATCHED", "MISMATCH", "LIFESTYLE", "NO_FACE_IN_PRIMARY", "LOAD_ERROR"
        @JsonProperty("is_match")
        private boolean isMatch;
        @JsonProperty("is_blocked")
        private boolean isBlocked;
        @JsonProperty("similarity_score")
        private double similarityScore;
        @JsonProperty("confidence_percent")
        private double confidencePercent;
        @JsonProperty("faces_detected")
        private int facesDetected;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BatchMatchResult {
        @JsonProperty("overall_status")
        private String overallStatus;
        @JsonProperty("is_profile_verified")
        private boolean isProfileVerified;
        @JsonProperty("total_photos")
        private int totalPhotos;
        @JsonProperty("matched_count")
        private int matchedCount;
        @JsonProperty("mismatch_count")
        private int mismatchCount;
        @JsonProperty("lifestyle_count")
        private int lifestyleCount;
        @JsonProperty("selfie_faces_detected")
        private int selfieFacesDetected;
        @JsonProperty("primary_portrait_matched")
        private boolean primaryPortraitMatched;
        private List<PhotoSlotVerificationResultDto> results;
        @JsonProperty("summary_message")
        private String summaryMessage;
    }

    public BatchMatchResult matchAllPhotos(String selfieBase64OrUrl, List<PhotoSlotItemDto> photos) {
        FeatureFlagsProperties.FaceMatch config = properties.getFeatures().getFacematch();
        if (config == null || !config.isEnabled()) {
            log.info("FaceMatch feature is disabled in config. Simulating batch verification pass.");
            List<PhotoSlotVerificationResultDto> simResults = new ArrayList<>();
            for (PhotoSlotItemDto p : photos) {
                simResults.add(PhotoSlotVerificationResultDto.builder()
                        .slot(p.getSlot())
                        .photoUrl(p.getPhotoUrl())
                        .status("MATCHED")
                        .isMatch(true)
                        .isBlocked(false)
                        .similarityScore(0.92)
                        .confidencePercent(96.0)
                        .facesDetected(1)
                        .message("Face verified (simulated).")
                        .build());
            }
            return BatchMatchResult.builder()
                    .overallStatus("VERIFIED")
                    .isProfileVerified(true)
                    .totalPhotos(photos.size())
                    .matchedCount(photos.size())
                    .mismatchCount(0)
                    .lifestyleCount(0)
                    .selfieFacesDetected(1)
                    .primaryPortraitMatched(true)
                    .results(simResults)
                    .summaryMessage("Simulated biometric pass for all photos.")
                    .build();
        }

        String serviceUrl = config.getServiceUrl();
        if (serviceUrl == null || serviceUrl.trim().isEmpty()) {
            serviceUrl = "http://facematch:5000";
        }
        String endpoint = serviceUrl.replaceAll("/+$", "") + "/match-all-photos";

        log.info("Dispatching multi-photo biometric batch matching to: {} for {} photos", endpoint, photos.size());

        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(10000);
            factory.setReadTimeout(30000);
            RestTemplate restTemplate = new RestTemplate(factory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            if (selfieBase64OrUrl != null && selfieBase64OrUrl.startsWith("data:image")) {
                body.put("selfie_base64", selfieBase64OrUrl);
            } else {
                body.put("selfie_url", selfieBase64OrUrl);
            }
            body.put("photos", photos);
            body.put("threshold", config.getThreshold());

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<BatchMatchResult> response = restTemplate.postForEntity(
                    endpoint,
                    requestEntity,
                    BatchMatchResult.class
            );

            BatchMatchResult resBody = response.getBody();
            if (resBody == null) {
                throw new IllegalStateException("Empty response from facematch /match-all-photos");
            }

            log.info("Batch face match completed: overall={}, verified={}, matched={}, mismatches={}, lifestyled={}",
                    resBody.getOverallStatus(), resBody.isProfileVerified(),
                    resBody.getMatchedCount(), resBody.getMismatchCount(), resBody.getLifestyleCount());

            return resBody;
        } catch (Exception e) {
            log.error("Failed to connect to FaceMatch /match-all-photos at {}: {}", endpoint, e.getMessage());
            return BatchMatchResult.builder()
                    .overallStatus("SERVICE_UNAVAILABLE")
                    .isProfileVerified(false)
                    .totalPhotos(photos.size())
                    .matchedCount(0)
                    .mismatchCount(0)
                    .lifestyleCount(0)
                    .selfieFacesDetected(0)
                    .primaryPortraitMatched(false)
                    .results(new ArrayList<>())
                    .summaryMessage("Face verification engine is temporarily busy. Will retry automatically.")
                    .build();
        }
    }
}
