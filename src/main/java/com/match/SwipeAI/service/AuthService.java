package com.match.SwipeAI.service;

import com.match.SwipeAI.dto.AuthDto;
import com.match.SwipeAI.enums.*;
import com.match.SwipeAI.model.Profile;
import com.match.SwipeAI.model.User;
import com.match.SwipeAI.repository.ProfileRepository;
import com.match.SwipeAI.repository.UserRepository;
import com.match.SwipeAI.security.JwtUtil;
import com.match.SwipeAI.service.integration.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final OtpService otpService;
    private final JwtUtil jwtUtil;

    public static String normalizePhone(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("[^0-9+]", "");
        if (!cleaned.startsWith("+")) {
            if (cleaned.length() == 10) {
                cleaned = "+91" + cleaned;
            } else if (cleaned.startsWith("91") && cleaned.length() == 12) {
                cleaned = "+" + cleaned;
            } else if (cleaned.startsWith("0") && cleaned.length() == 11) {
                cleaned = "+91" + cleaned.substring(1);
            } else {
                cleaned = "+" + cleaned;
            }
        }
        return cleaned;
    }

    public String sendOtp(String phoneE164, String channel) {
        return otpService.sendOtp(normalizePhone(phoneE164), channel);
    }

    @Transactional
    public AuthDto.AuthResponse verifyOtpAndLogin(AuthDto.VerifyOtpRequest request) {
        String normalizedPhone = normalizePhone(request.getPhoneE164());
        boolean valid = otpService.verifyOtp(normalizedPhone, request.getOtp());
        if (!valid) {
            throw new IllegalArgumentException("Invalid or expired OTP. Please try again.");
        }

        boolean isWhatsApp = "whatsapp".equalsIgnoreCase(request.getChannel());

        return getOrCreateUser(
                normalizedPhone,
                isWhatsApp,
                request.getGender(),
                request.getIntent(),
                request.getBirthDate()
        );
    }

    @Transactional
    public AuthDto.AuthResponse loginWithWhatsApp(AuthDto.WhatsAppLoginRequest request) {
        String code = request.getOtp() != null && !request.getOtp().isBlank() ? request.getOtp() : request.getAuthCode();
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("WhatsApp verification code is required.");
        }

        return verifyOtpAndLogin(new AuthDto.VerifyOtpRequest(
                request.getPhoneE164(),
                code.trim(),
                "whatsapp",
                request.getGender(),
                request.getIntent(),
                request.getBirthDate()
        ));
    }

    private AuthDto.AuthResponse getOrCreateUser(String phone, boolean isWhatsApp, Gender gender, DatingIntent intent, LocalDate birthDate) {
        Optional<User> optionalUser = userRepository.findByPhoneE164(phone);
        boolean isNew = optionalUser.isEmpty();

        User user;
        if (isNew) {
            user = User.builder()
                    .phoneE164(phone)
                    .whatsappVerified(isWhatsApp)
                    .digilockerVerified(false)
                    .livenessScore(0.0)
                    .karmaScore(100)
                    .gender(gender)
                    .intent(intent)
                    .birthDate(birthDate)
                    .latitude(null)
                    .longitude(null)
                    .sparksBalance(3)
                    .boostsBalance(0)
                    .directDmsBalance(0)
                    .hasActivePass(false)
                    .build();
            user = userRepository.save(user);

            // Create clean baseline initial profile without any hardcoded mock data
            Profile profile = Profile.builder()
                    .userId(user.getId())
                    .displayName("")
                    .bio("")
                    .languagesSpoken(List.of())
                    .photosJson("[]")
                    .build();
            profileRepository.save(profile);
        } else {
            user = optionalUser.get();
            if (isWhatsApp && !Boolean.TRUE.equals(user.getWhatsappVerified())) {
                user.setWhatsappVerified(true);
                user = userRepository.save(user);
            }
        }

        String token = jwtUtil.generateToken(user.getId(), user.getPhoneE164());

        return AuthDto.AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .phoneE164(user.getPhoneE164())
                .isNewUser(isNew)
                .digilockerVerified(Boolean.TRUE.equals(user.getDigilockerVerified()))
                .whatsappVerified(Boolean.TRUE.equals(user.getWhatsappVerified()))
                .livenessScore(user.getLivenessScore() != null ? user.getLivenessScore() : 0.0)
                .karmaScore(user.getKarmaScore() != null ? user.getKarmaScore() : 100)
                .sparksBalance(user.getSparksBalance() != null ? user.getSparksBalance() : 0)
                .hasActivePass(Boolean.TRUE.equals(user.getHasActivePass()))
                .build();
    }
}
