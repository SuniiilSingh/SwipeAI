package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.AuthDto;
import com.match.SwipeAI.model.User;
import com.match.SwipeAI.repository.UserRepository;
import com.match.SwipeAI.service.AuthService;
import com.match.SwipeAI.service.ProfileService;
import com.match.SwipeAI.service.integration.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Authentication and Session Controller.
 * Provides endpoints for mobile OTP and WhatsApp 1-tap instant verification,
 * as well as secure OTP-verified account and data deletion per App Store & DPDP rules.
 */
@CrossOrigin
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final com.match.SwipeAI.config.FeatureFlagsProperties properties;
    private final UserRepository userRepository;
    private final ProfileService profileService;
    private final OtpService otpService;

    /**
     * Dispatch an OTP to the given phone number via WhatsApp or Twilio SMS.
     *
     * @param request Contains phone number in E.164 format (+918910653499)
     * @return Confirmation response with channel and OTP length
     */
    @PostMapping("/otp/send")
    public ResponseEntity<Map<String, Object>> sendOtp(@Valid @RequestBody AuthDto.SendOtpRequest request) {
        String channel = (request.getChannel() != null && !request.getChannel().isBlank())
                ? request.getChannel().toLowerCase().trim()
                : "sms";
        authService.sendOtp(request.getPhoneE164(), channel);
        String channelName = "whatsapp".equalsIgnoreCase(channel) ? "WhatsApp" : "SMS";

        boolean isMock = properties.getFeatures().getTwilio().isMockOtp();
        String message = isMock
                ? "⚡ Test Mode Active: Use verification code 123456"
                : "OTP sent via " + channelName;

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "channel", channel,
                "message", message,
                "isMockOtp", isMock,
                "mockOtp", isMock ? "123456" : "",
                "otpLength", 6
        ));
    }

    /**
     * Verify mobile OTP and create or authenticate user session.
     *
     * @param request Contains phone, entered OTP, intent, gender, and birth date
     * @return AuthResponse with signed JWT bearer token and verification badges
     */
    @PostMapping("/otp/verify")
    public ResponseEntity<AuthDto.AuthResponse> verifyOtp(@Valid @RequestBody AuthDto.VerifyOtpRequest request) {
        AuthDto.AuthResponse response = authService.verifyOtpAndLogin(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Authenticate via WhatsApp 1-tap instant login, avoiding Indian telecom SMS drops.
     *
     * @param request WhatsApp auth payload
     * @return AuthResponse with JWT and WhatsApp Verified badge
     */
    @PostMapping("/whatsapp/login")
    public ResponseEntity<AuthDto.AuthResponse> loginWhatsApp(@Valid @RequestBody AuthDto.WhatsAppLoginRequest request) {
        AuthDto.AuthResponse response = authService.loginWithWhatsApp(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Authenticate via Firebase Phone Auth ID Token.
     */
    @PostMapping("/firebase-login")
    public ResponseEntity<AuthDto.AuthResponse> firebaseLogin(@Valid @RequestBody AuthDto.FirebaseLoginRequest request) {
        AuthDto.AuthResponse response = authService.loginWithFirebase(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get active authentication provider configuration and fallbacks.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getAuthConfig() {
        com.match.SwipeAI.config.FeatureFlagsProperties.Firebase firebase = properties.getFeatures().getFirebase();
        com.match.SwipeAI.config.FeatureFlagsProperties.SecondaryOtp secondary = properties.getFeatures().getSecondaryOtp();
        com.match.SwipeAI.config.FeatureFlagsProperties.Twilio twilio = properties.getFeatures().getTwilio();

        boolean hasFirebase = firebase != null && firebase.isEnabled();
        boolean hasSecondary = (secondary != null && secondary.isEnabled() && secondary.getApiKey() != null && !secondary.getApiKey().isBlank())
                || (twilio != null && twilio.isEnabled() && twilio.getAccountSid() != null && !twilio.getAccountSid().isBlank());

        String secondaryProvider = "none";
        if (secondary != null && secondary.isEnabled()) {
            secondaryProvider = secondary.getProvider();
        } else if (twilio != null && twilio.isEnabled()) {
            secondaryProvider = "twilio";
        }

        return ResponseEntity.ok(Map.of(
                "primaryProvider", "firebase",
                "firebaseEnabled", hasFirebase,
                "firebaseConfig", Map.of(
                        "apiKey", firebase != null ? firebase.getApiKey() : "",
                        "projectId", firebase != null ? firebase.getProjectId() : "",
                        "authDomain", firebase != null ? firebase.getAuthDomain() : ""
                ),
                "secondaryConfigured", hasSecondary,
                "secondaryProvider", secondaryProvider,
                "isMockOtp", twilio != null && twilio.isMockOtp()
        ));
    }

    /**
     * Dispatch OTP to user's registered phone number for account and data deletion verification.
     */
    @PostMapping("/delete-account/send-otp")
    public ResponseEntity<Map<String, Object>> sendDeleteAccountOtp(@RequestBody Map<String, String> body) {
        String rawPhone = body != null ? body.get("phone") : null;
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobile number is required.");
        }
        String normalized = AuthService.normalizePhone(rawPhone);
        User user = userRepository.findByPhoneE164(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No registered account found with mobile number " + rawPhone + ". Please check your number."));

        otpService.sendOtp(normalized, "sms");
        boolean isMock = properties.getFeatures().getTwilio().isMockOtp();

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Verification OTP sent to " + maskPhoneHelper(normalized),
                "phoneMasked", maskPhoneHelper(normalized),
                "isMockOtp", isMock,
                "mockOtp", isMock ? "123456" : ""
        ));
    }

    /**
     * Verify submitted OTP, record user churn remarks, and purge account per DPDP / App Store rules.
     */
    @PostMapping("/delete-account/verify-and-delete")
    public ResponseEntity<Map<String, Object>> verifyAndDeleteAccount(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String rawPhone = body != null ? body.get("phone") : null;
        String otp = body != null ? body.get("otp") : null;
        String reason = body != null ? body.get("reason") : "WEB_DATA_DELETION";
        String remarks = body != null ? body.get("remarks") : null;

        if (rawPhone == null || rawPhone.isBlank() || otp == null || otp.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobile number and OTP code are required.");
        }

        String normalized = AuthService.normalizePhone(rawPhone);
        boolean valid = otpService.verifyOtp(normalized, otp.trim());
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code. Please try again.");
        }

        User user = userRepository.findByPhoneE164(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found or already deleted."));

        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) clientIp = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        profileService.deleteProfileWithAudit(user.getId(), "WEB_OTP_VERIFIED", reason, remarks, clientIp, userAgent);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Your account and all associated personal data have been permanently erased from BlunderR.",
                "phoneMasked", maskPhoneHelper(normalized)
        ));
    }

    private String maskPhoneHelper(String p) {
        if (p == null || p.length() < 7) return p;
        int len = p.length();
        return p.substring(0, 3) + " " + p.substring(3, Math.min(5, len)) + "*****" + p.substring(Math.max(0, len - 3));
    }
}
