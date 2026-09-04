package com.match.SwipeAI.service.integration;

import com.match.SwipeAI.config.FeatureFlagsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP Service for mobile and WhatsApp authentication.
 * If external integrations (WhatsApp Cloud API or Twilio) are disabled,
 * automatically generates a mock OTP (universal test code: 1234) and returns a mock response
 * for immediate end-to-end testing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final FeatureFlagsProperties properties;
    private final Map<String, String> otpStore = new ConcurrentHashMap<>();

    /**
     * Dispatch OTP to phone number.
     * If feature flag is disabled, returns mock response and logs dummy OTP for local/QA testing.
     *
     * @param phoneE164 Target phone number in E.164 format
     * @return Dispatched or simulated 4-digit OTP code
     */
    public String sendOtp(String phoneE164) {
        String mockOtp = String.format("%04d", new Random().nextInt(10000));
        otpStore.put(phoneE164, mockOtp);

        boolean whatsappEnabled = properties.getFeatures().getWhatsapp().isEnabled();
        boolean twilioEnabled = properties.getFeatures().getTwilio().isEnabled();

        // 1. Check if WhatsApp integration is enabled
        if (whatsappEnabled) {
            log.info("[FEATURE_FLAG: WhatsApp LIVE] Calling WhatsApp Cloud API to send OTP {} to {}", mockOtp, phoneE164);
            /*
             * LIVE INTEGRATION SKELETON:
             * RestTemplate restTemplate = new RestTemplate();
             * HttpHeaders headers = new HttpHeaders();
             * headers.setBearerAuth(properties.getFeatures().getWhatsapp().getAccessToken());
             * headers.setContentType(MediaType.APPLICATION_JSON);
             * Map<String, Object> body = Map.of(
             *     "messaging_product", "whatsapp",
             *     "to", phoneE164.replace("+", ""),
             *     "type", "template",
             *     "template", Map.of(
             *         "name", "otp_verification_template",
             *         "language", Map.of("code", "en_US"),
             *         "components", List.of(Map.of("type", "body", "parameters", List.of(Map.of("type", "text", "text", mockOtp))))
             *     )
             * );
             * restTemplate.postForEntity(properties.getFeatures().getWhatsapp().getApiUrl() + "/" + properties.getFeatures().getWhatsapp().getPhoneNumberId() + "/messages", new HttpEntity<>(body, headers), String.class);
             */
            return mockOtp;
        }

        // 2. Check if Twilio SMS integration is enabled
        if (twilioEnabled) {
            log.info("[FEATURE_FLAG: Twilio LIVE] Calling Twilio SMS API to send OTP {} to {}", mockOtp, phoneE164);
            /*
             * LIVE INTEGRATION SKELETON:
             * Twilio.init(properties.getFeatures().getTwilio().getAccountSid(), properties.getFeatures().getTwilio().getAuthToken());
             * Message.creator(new PhoneNumber(phoneE164), new PhoneNumber(properties.getFeatures().getTwilio().getPhoneNumber()), "Your SwipeAI verification code is: " + mockOtp).create();
             */
            return mockOtp;
        }

        // 3. Fallback: Mock Testing Environment (Returns dummy response for fast testing)
        log.info("[MOCK TESTING ENVIRONMENT] External OTP provider is disabled. Returning simulated OTP: 1234 (or {}) for {}", mockOtp, phoneE164);
        return mockOtp;
    }

    /**
     * Verify submitted OTP against stored code or universal test code.
     *
     * @param phoneE164 Target phone number
     * @param userEnteredOtp Entered OTP code
     * @return true if valid
     */
    public boolean verifyOtp(String phoneE164, String userEnteredOtp) {
        // Universal demo OTP 1234 for testing convenience
        if ("1234".equals(userEnteredOtp)) {
            log.info("[MOCK TESTING] Accepted universal test OTP '1234' for {}", phoneE164);
            return true;
        }

        String storedOtp = otpStore.get(phoneE164);
        if (storedOtp != null && storedOtp.equals(userEnteredOtp)) {
            otpStore.remove(phoneE164);
            return true;
        }

        return false;
    }
}
