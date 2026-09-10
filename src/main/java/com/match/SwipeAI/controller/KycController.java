package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.KycDto;
import com.match.SwipeAI.model.User;
import com.match.SwipeAI.repository.UserRepository;
import com.match.SwipeAI.service.integration.DigiLockerKycService;
import com.match.SwipeAI.service.integration.LivenessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Zero-Knowledge KYC and Biometric Liveness Verification Controller.
 * Manages DigiLocker ZK-proof generation, age 18+ and gender verification,
 * and 3D selfie head turn liveness checks to prevent deepfakes and stolen profiles.
 */
@RestController
@RequestMapping("/v1/kyc")
@RequiredArgsConstructor
public class KycController {

    private final DigiLockerKycService digiLockerKycService;
    private final LivenessService livenessService;
    private final UserRepository userRepository;
    private final com.match.SwipeAI.config.FeatureFlagsProperties featureFlagsProperties;

    /**
     * Return active KYC feature flags.
     */
    @GetMapping("/features")
    public ResponseEntity<java.util.Map<String, Object>> getKycFeatureStatus() {
        return ResponseEntity.ok(java.util.Map.of(
                "digilockerEnabled", featureFlagsProperties.getFeatures().getDigilocker().isEnabled(),
                "livenessEnabled", true
        ));
    }

    /**
     * Initiate DigiLocker OAuth2 / ZK session and return authorization URL.
     *
     * @param userId Authenticated user UUID from JWT
     * @return OAuth URL and session state token
     */
    @PostMapping("/digilocker/initiate")
    public ResponseEntity<KycDto.DigiLockerInitiateResponse> initiateDigiLocker(@AuthenticationPrincipal UUID userId) {
        if (!featureFlagsProperties.getFeatures().getDigilocker().isEnabled()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(KycDto.DigiLockerInitiateResponse.builder()
                            .simulated(false)
                            .message("DigiLocker KYC is disabled in this version build.")
                            .build());
        }
        KycDto.DigiLockerInitiateResponse response = digiLockerKycService.initiateKyc(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Verify DigiLocker authorization code / cryptographic ZK proof assertion.
     * Confirms Age >= 18 and Gender, assigns Gold Shield Badge without storing Aadhaar numbers.
     *
     * @param userId Authenticated user UUID
     * @param request Contains state token and authorization code
     * @return Verification status and Gold Shield Badge
     */
    @PostMapping("/digilocker/verify-proof")
    public ResponseEntity<KycDto.DigiLockerProofResponse> verifyDigiLockerProof(
            @AuthenticationPrincipal UUID userId,
            @RequestBody KycDto.DigiLockerProofRequest request) {
        if (!featureFlagsProperties.getFeatures().getDigilocker().isEnabled()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(KycDto.DigiLockerProofResponse.builder()
                            .isVerified(false)
                            .message("DigiLocker KYC is disabled in this version build.")
                            .build());
        }
        KycDto.DigiLockerProofResponse response = digiLockerKycService.verifyProof(userId, request);

        if (response.isVerified()) {
            userRepository.findById(userId).ifPresent(user -> {
                user.setDigilockerVerified(true);
                user.setDigilockerSubHash(digiLockerKycService.computeSubjectHash(userId.toString()));
                userRepository.save(user);
            });
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Evaluate 3-second head turn 3D biometric selfie scan to eliminate deepfakes.
     *
     * @param userId Authenticated user UUID
     * @param request Contains head turn duration and frame payload
     * @return Liveness score (>= 0.85 indicates live human)
     */
    @PostMapping("/liveness/verify")
    public ResponseEntity<KycDto.LivenessResponse> verifyLiveness(
            @AuthenticationPrincipal UUID userId,
            @RequestBody KycDto.LivenessRequest request) {
        KycDto.LivenessResponse response = livenessService.verifyLiveness(userId, request);

        if (response.isLiveHuman()) {
            userRepository.findById(userId).ifPresent(user -> {
                user.setLivenessScore(response.getLivenessScore());
                userRepository.save(user);
            });
        }

        return ResponseEntity.ok(response);
    }
}
