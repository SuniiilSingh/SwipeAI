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
import java.util.Base64;

/**
 * OTP Service for mobile SMS and WhatsApp authentication.
 * Uses Twilio Verify exclusively for both SMS and WhatsApp channels.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final FeatureFlagsProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    /**
     * Dispatches an OTP code via Twilio Verify ("sms" or "whatsapp").
     *
     * @param phoneE164 Target phone number in E.164 format
     * @param channel Delivery channel ("sms" or "whatsapp")
     * @return Dispatched confirmation token
     */
    public String sendOtp(String phoneE164, String channel) {
        String targetChannel = "whatsapp".equalsIgnoreCase(channel) ? "whatsapp" : "sms";
        log.info("[TWILIO VERIFY] Dispatching OTP via {} to {}", targetChannel, phoneE164);

        FeatureFlagsProperties.Twilio twilio = getTwilio();
        String formData = "To=" + URLEncoder.encode(phoneE164, StandardCharsets.UTF_8)
                + "&Channel=" + URLEncoder.encode(targetChannel, StandardCharsets.UTF_8);

        try {
            HttpResponse<String> response = postToTwilioVerify(twilio, "/Verifications", formData);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[TWILIO VERIFY SUCCESS] Dispatched {} verification to {}", targetChannel, phoneE164);
                return "VERIFY_CODE_SENT";
            }
            String responseBody = response.body() != null ? response.body() : "";
            log.error("[TWILIO VERIFY ERROR] Status {}: {}", response.statusCode(), responseBody);

            if (response.statusCode() == 429 || responseBody.contains("60203")) {
                throw new IllegalStateException("Too many attempts. Retry in 10 mins.");
            }

            throw new IllegalStateException("Twilio Verify failed (" + response.statusCode() + "): " + responseBody);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TWILIO VERIFY ERROR] Connection error for {}: {}", phoneE164, e.getMessage());
            throw new IllegalStateException("Twilio Verify connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies submitted OTP code against Twilio Verify API.
     *
     * @param phoneE164 Target phone number
     * @param userEnteredOtp Entered OTP code
     * @return true if approved
     */
    public boolean verifyOtp(String phoneE164, String userEnteredOtp) {
        if (userEnteredOtp == null || userEnteredOtp.isBlank()) {
            return false;
        }

        try {
            FeatureFlagsProperties.Twilio twilio = getTwilio();
            String formData = "To=" + URLEncoder.encode(phoneE164, StandardCharsets.UTF_8)
                    + "&Code=" + URLEncoder.encode(userEnteredOtp.trim(), StandardCharsets.UTF_8);

            HttpResponse<String> response = postToTwilioVerify(twilio, "/VerificationCheck", formData);
            log.info("[TWILIO VERIFY CHECK] Status: {}, Response: {}", response.statusCode(), response.body());
            return response.statusCode() >= 200 && response.statusCode() < 300
                    && response.body().contains("\"status\": \"approved\"");
        } catch (Exception e) {
            log.error("[TWILIO VERIFY CHECK ERROR] Verification failed for {}: {}", phoneE164, e.getMessage());
            return false;
        }
    }

    private FeatureFlagsProperties.Twilio getTwilio() {
        FeatureFlagsProperties.Twilio twilio = properties.getFeatures().getTwilio();
        if (twilio.getVerifyServiceSid() == null || twilio.getVerifyServiceSid().isBlank()) {
            throw new IllegalStateException("Twilio Verify Service SID is not configured.");
        }
        if (twilio.getAccountSid() == null || twilio.getAccountSid().isBlank()
                || twilio.getAuthToken() == null || twilio.getAuthToken().isBlank()) {
            throw new IllegalStateException("Twilio credentials are not configured. Please check account-sid and auth-token.");
        }
        return twilio;
    }

    private HttpResponse<String> postToTwilioVerify(FeatureFlagsProperties.Twilio twilio, String path, String formData) throws Exception {
        String url = "https://verify.twilio.com/v2/Services/" + twilio.getVerifyServiceSid() + path;
        String auth = Base64.getEncoder().encodeToString((twilio.getAccountSid() + ":" + twilio.getAuthToken()).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
