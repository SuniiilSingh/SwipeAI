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
     * In demo mode, generates and stores a 4-digit mock OTP (universal: 1234).
     *
     * @param request Contains phone number in E.164 format (+919876543210)
     * @return Confirmation message with demo OTP hint
     */
    @PostMapping("/otp/send")
    public ResponseEntity<Map<String, Object>> sendOtp(@Valid @RequestBody AuthDto.SendOtpRequest request) {
        String otp = authService.sendOtp(request.getPhoneE164());
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "OTP sent successfully. In demo mode, use OTP 1234 or " + otp,
                "demoOtp", otp
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
