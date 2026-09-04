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

    public String sendOtp(String phoneE164) {
        return otpService.sendOtp(phoneE164);
    }

    @Transactional
    public AuthDto.AuthResponse verifyOtpAndLogin(AuthDto.VerifyOtpRequest request) {
        boolean valid = otpService.verifyOtp(request.getPhoneE164(), request.getOtp());
        if (!valid) {
            throw new IllegalArgumentException("Invalid or expired OTP. Use 1234 in demo mode.");
        }

        return getOrCreateUser(
                request.getPhoneE164(),
                false,
                request.getGender(),
                request.getIntent(),
                request.getBirthDate()
        );
    }

    @Transactional
    public AuthDto.AuthResponse loginWithWhatsApp(AuthDto.WhatsAppLoginRequest request) {
        // WhatsApp 1-tap direct auth
        return getOrCreateUser(
                request.getPhoneE164(),
                true,
                request.getGender(),
                request.getIntent(),
                request.getBirthDate()
        );
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
                    .gender(gender != null ? gender : Gender.FEMALE)
                    .intent(intent != null ? intent : DatingIntent.SERIOUS_DATING)
                    .birthDate(birthDate != null ? birthDate : LocalDate.of(2000, 5, 15))
                    .latitude(12.9716) // Default Bengaluru coordinates
                    .longitude(77.5946)
                    .sparksBalance(3)
                    .boostsBalance(0)
                    .directDmsBalance(0)
                    .hasActivePass(false)
                    .build();
            user = userRepository.save(user);

            // Create baseline initial profile
            Profile profile = Profile.builder()
                    .userId(user.getId())
                    .displayName("Single in Bangalore")
                    .bio("Design, specialty coffee, and indie pop.")
                    .dietaryPref(DietaryPreference.PURE_VEG)
                    .livingStatus(LivingStatus.INDEPENDENT_FLAT)
                    .languagesSpoken(List.of("English", "Hindi", "Kannada"))
                    .zodiacSign("Leo")
                    .sunSign("Leo")
                    .moonSign("Scorpio")
                    .city("Bengaluru")
                    .neighborhood("Indiranagar")
                    .microCircle("Koramangala Tech Founders")
                    .photosJson("[\"https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500\"]")
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
