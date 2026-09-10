package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.AuthDto;
import com.match.SwipeAI.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication and Session Controller.
 * Provides endpoints for mobile OTP and WhatsApp 1-tap instant verification.
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "channel", channel,
                "message", "OTP sent via " + channelName,
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
}
