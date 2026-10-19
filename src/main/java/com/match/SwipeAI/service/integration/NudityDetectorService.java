package com.match.SwipeAI.service.integration;

import com.match.SwipeAI.config.FeatureFlagsProperties;
import com.match.SwipeAI.dto.ChatDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NudityDetectorService {

    private final FeatureFlagsProperties featureFlagsProperties;

    /**
     * Scan image from public URL (e.g., chat media attachment).
     */
    public ChatDto.NudityCheckResponse scanMedia(String mediaUrl) {
        log.info("Running Shield 360 AI Nudity & Sensitive Content classifier on media: {}", mediaUrl);
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return buildSafeResponse("No media URL provided.");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("image_url", mediaUrl);

        return executeDetection(body);
    }

    /**
     * Scan raw image bytes (e.g., direct profile photo upload).
     */
    public ChatDto.NudityCheckResponse scanImageBytes(byte[] imageBytes) {
        log.info("Running Shield 360 AI Nudity classifier on raw image bytes ({} bytes)", imageBytes != null ? imageBytes.length : 0);
        if (imageBytes == null || imageBytes.length == 0) {
            return buildSafeResponse("Empty image bytes.");
        }

        String base64Data = Base64.getEncoder().encodeToString(imageBytes);
        Map<String, Object> body = new HashMap<>();
        body.put("image_base64", base64Data);

        return executeDetection(body);
    }

    @SuppressWarnings("rawtypes")
    private ChatDto.NudityCheckResponse executeDetection(Map<String, Object> body) {
        String serviceUrl = "http://facematch:5000";
        try {
            if (featureFlagsProperties != null &&
                featureFlagsProperties.getFeatures() != null &&
                featureFlagsProperties.getFeatures().getFacematch() != null &&
                featureFlagsProperties.getFeatures().getFacematch().getServiceUrl() != null) {
                serviceUrl = featureFlagsProperties.getFeatures().getFacematch().getServiceUrl();
            }
        } catch (Exception ignored) {}

        String endpoint = serviceUrl.replaceAll("/+$", "") + "/detect-nsfw";

        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(4000);
            factory.setReadTimeout(8000);
            RestTemplate restTemplate = new RestTemplate(factory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, requestEntity, Map.class);
            Map resBody = response.getBody();

            if (resBody != null) {
                boolean isNsfw = Boolean.TRUE.equals(resBody.get("is_nsfw"));
                double score = resBody.get("nsfw_score") instanceof Number
                        ? ((Number) resBody.get("nsfw_score")).doubleValue()
                        : (isNsfw ? 0.90 : 0.05);
                String action = resBody.get("action") != null ? resBody.get("action").toString() : (isNsfw ? "BLUR_WITH_WARNING" : "ALLOW");
                String message = resBody.get("message") != null ? resBody.get("message").toString() : "Shield 360 evaluated";

                return ChatDto.NudityCheckResponse.builder()
                        .isSensitive(isNsfw)
                        .confidenceScore(score)
                        .action(action)
                        .message(message)
                        .build();
            }
        } catch (Exception e) {
            log.warn("Shield 360 AI Nudity detection service unavailable ({}); falling back to safe defaults.", e.getMessage());
        }

        return buildSafeResponse("Inference service fallback.");
    }

    private ChatDto.NudityCheckResponse buildSafeResponse(String msg) {
        return ChatDto.NudityCheckResponse.builder()
                .isSensitive(false)
                .confidenceScore(0.05)
                .action("ALLOW")
                .message(msg)
                .build();
    }
}
