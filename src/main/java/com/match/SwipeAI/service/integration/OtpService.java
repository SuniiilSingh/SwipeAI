package com.match.SwipeAI.service.integration;

import com.match.SwipeAI.config.FeatureFlagsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * OTP Service for secondary domestic fallback verification and mock testing.
 * Primary phone authentication is powered by Firebase Phone Auth.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final FeatureFlagsProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    public boolean isMockOtpEnabled() {
        FeatureFlagsProperties.SecondaryOtp secondary = properties.getFeatures().getSecondaryOtp();
        if (secondary == null) return false;
        return secondary.isMockOtp();
    }

    /**
     * Dispatches an OTP code via configured secondary domestic provider or mock testing.
     *
     * @param phoneE164 Target phone number in E.164 format
     * @param channel Delivery channel ("sms" or "whatsapp")
     * @return Dispatched confirmation token
     */
    public String sendOtp(String phoneE164, String channel) {
        String targetChannel = "whatsapp".equalsIgnoreCase(channel) ? "whatsapp" : "sms";

        if (isMockOtpEnabled()) {
            log.info("[MOCK OTP ACTIVE] Test OTP 123456 enabled for {}.", phoneE164);
            return "MOCK_OTP_SENT";
        }

        FeatureFlagsProperties.SecondaryOtp secondary = properties.getFeatures().getSecondaryOtp();
        String provider = secondary.getProvider() != null ? secondary.getProvider().toLowerCase() : "mock";

        log.info("[OTP DISPATCH] Dispatching via secondary provider ({}) to {}", provider, phoneE164);

        if ("2factor".equals(provider) && secondary.getApiKey() != null && !secondary.getApiKey().isBlank()) {
            try {
                String cleanPhone = phoneE164.replace("+", "");
                String url = "https://2factor.in/API/V1/" + secondary.getApiKey() + "/SMS/" + cleanPhone + "/AUTOGEN";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    log.info("[2FACTOR SUCCESS] Dispatched OTP to {}", phoneE164);
                    return "VERIFY_CODE_SENT";
                }
            } catch (Exception e) {
                log.error("[2FACTOR ERROR] Failed to dispatch to {}: {}. Falling back to mock.", phoneE164, e.getMessage());
            }
        }

        // Fallback
        log.info("[FALLBACK] Secondary gateway simulated dispatch for {}.", phoneE164);
        return "MOCK_OTP_SENT";
    }

    /**
     * Verifies submitted OTP code.
     *
     * @param phoneE164 Target phone number
     * @param userEnteredOtp Entered OTP code
     * @return true if approved
     */
    public boolean verifyOtp(String phoneE164, String userEnteredOtp) {
        if (userEnteredOtp == null || userEnteredOtp.isBlank()) {
            return false;
        }

        String cleanOtp = userEnteredOtp.trim();
        if (isMockOtpEnabled()) {
            boolean valid = "123456".equals(cleanOtp);
            log.info("[OTP CHECK] Phone: {}, Code: {}, Result: {}", phoneE164, cleanOtp, valid ? "APPROVED" : "REJECTED");
            return valid;
        }

        // Secondary provider verification (e.g. 2factor)
        FeatureFlagsProperties.SecondaryOtp secondary = properties.getFeatures().getSecondaryOtp();
        String provider = secondary != null && secondary.getProvider() != null ? secondary.getProvider().toLowerCase() : "";
        if ("2factor".equals(provider) && secondary.getApiKey() != null && !secondary.getApiKey().isBlank()) {
            try {
                String cleanPhone = phoneE164.replace("+", "");
                String url = "https://2factor.in/API/V1/" + secondary.getApiKey() + "/SMS/VERIFY3/" + cleanPhone + "/" + cleanOtp;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() >= 200 && response.statusCode() < 300 && response.body().contains("OTP Matched");
            } catch (Exception e) {
                log.error("[2FACTOR VERIFY ERROR] Failed to verify for {}: {}", phoneE164, e.getMessage());
                return false;
            }
        }

        return false;
    }
}
