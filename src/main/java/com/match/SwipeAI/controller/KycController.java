package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.KycDto;
import com.match.SwipeAI.model.User;
import com.match.SwipeAI.repository.UserRepository;
import com.match.SwipeAI.service.integration.DigiLockerKycService;
import com.match.SwipeAI.service.integration.FaceMatchService;
import com.match.SwipeAI.service.integration.LivenessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Zero-Knowledge KYC and Biometric Liveness Verification Controller.
 * Manages DigiLocker ZK-proof generation, age 18+ and gender verification,
 * and 3D selfie head turn liveness checks to prevent deepfakes and stolen profiles.
 */
@Slf4j
@RestController
@RequestMapping("/v1/kyc")
@RequiredArgsConstructor
public class KycController {

    private final DigiLockerKycService digiLockerKycService;
    private final LivenessService livenessService;
    private final FaceMatchService faceMatchService;
    private final com.match.SwipeAI.repository.ProfileRepository profileRepository;
    private final com.match.SwipeAI.service.ProfileService profileService;
    private final com.match.SwipeAI.service.integration.R2StorageService r2StorageService;
    private final UserRepository userRepository;
    private final com.match.SwipeAI.config.FeatureFlagsProperties featureFlagsProperties;

    /**
     * Return active KYC feature flags.
     */
    @GetMapping("/features")
    public ResponseEntity<java.util.Map<String, Object>> getKycFeatureStatus() {
        return ResponseEntity.ok(java.util.Map.of(
                "digilockerEnabled", featureFlagsProperties.getFeatures().getDigilocker().isEnabled(),
                "facematchEnabled", featureFlagsProperties.getFeatures().getFacematch().isEnabled(),
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
     * Immediately marks verification status as PENDING and verifies selfie in the background.
     *
     * @param userId Authenticated user UUID
     * @param request Contains head turn duration and frame payload
     * @return Liveness score (>= 0.85 indicates live human)
     */
    @PostMapping("/liveness/verify")
    public ResponseEntity<KycDto.LivenessResponse> verifyLiveness(
            @AuthenticationPrincipal UUID userId,
            @RequestBody(required = false) KycDto.LivenessRequest request) {
        if (request == null) {
            request = new KycDto.LivenessRequest();
        }

        KycDto.LivenessResponse response = livenessService.verifyLiveness(userId, request);

        if (response.isLiveHuman()) {
            String savedSelfieUrl = null;
            if (request.getSelfieFrameBase64() != null && !request.getSelfieFrameBase64().trim().isEmpty()) {
                try {
                    String raw = request.getSelfieFrameBase64().trim();
                    if (raw.startsWith("http://") || raw.startsWith("https://")) {
                        savedSelfieUrl = raw;
                    } else {
                        String cleanBase64 = raw.replaceAll("^data:image/[a-zA-Z]+;base64,", "");
                        byte[] selfieBytes = java.util.Base64.getDecoder().decode(cleanBase64);
                        var uploadRes = r2StorageService.uploadFile("selfie_" + userId + ".jpg", "image/jpeg", selfieBytes);
                        savedSelfieUrl = uploadRes.get("publicUrl");
                    }
                } catch (Exception e) {
                    log.warn("Could not store liveness selfie frame for user {}: {}", userId, e.getMessage());
                }
            }

            final String finalSelfieUrl = savedSelfieUrl;
            userRepository.findById(userId).ifPresent(user -> {
                user.setLivenessScore(response.getLivenessScore());
                user.setFaceVerified(false);
                userRepository.save(user);
            });

            com.match.SwipeAI.model.Profile profile = profileRepository.findById(userId)
                    .orElseGet(() -> com.match.SwipeAI.model.Profile.builder().userId(userId).build());
            if (finalSelfieUrl != null) {
                profile.setSelfieUrl(finalSelfieUrl);
            }
            profile.setVerificationStatus("PENDING");
            profileRepository.save(profile);

            profileService.triggerAsyncSelfieVerification(userId);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Submit a selfie asynchronously: immediately marks verificationStatus = PENDING
     * and runs background AI verification without blocking the user.
     */
    @PostMapping("/selfie/submit-async")
    public ResponseEntity<KycDto.FaceMatchResponse> submitSelfieAsync(
            @AuthenticationPrincipal UUID userId,
            @RequestBody KycDto.FaceMatchRequest request) {
        if (request == null || request.getSelfieBase64() == null || request.getSelfieBase64().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(KycDto.FaceMatchResponse.builder()
                    .verified(false)
                    .status("MISSING_SELFIE")
                    .message("Selfie image is required.")
                    .build());
        }

        String savedSelfieUrl = null;
        String raw = request.getSelfieBase64().trim();
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            savedSelfieUrl = raw;
        } else {
            try {
                String cleanBase64 = raw.replaceAll("^data:image/[a-zA-Z]+;base64,", "");
                byte[] selfieBytes = java.util.Base64.getDecoder().decode(cleanBase64);
                var uploadRes = r2StorageService.uploadFile("selfie_" + userId + ".jpg", "image/jpeg", selfieBytes);
                savedSelfieUrl = uploadRes.get("publicUrl");
            } catch (Exception e) {
                log.warn("Failed to decode/upload async selfie for user {}: {}", userId, e.getMessage());
            }
        }

        userRepository.findById(userId).ifPresent(user -> {
            user.setFaceVerified(false);
            userRepository.save(user);
        });

        com.match.SwipeAI.model.Profile profile = profileRepository.findById(userId)
                .orElseGet(() -> com.match.SwipeAI.model.Profile.builder().userId(userId).build());
        if (savedSelfieUrl != null) {
            profile.setSelfieUrl(savedSelfieUrl);
        }
        profile.setVerificationStatus("PENDING");
        profileRepository.save(profile);

        profileService.triggerAsyncSelfieVerification(userId);

        return ResponseEntity.ok(KycDto.FaceMatchResponse.builder()
                .verified(false)
                .status("PENDING")
                .message("Verification Pending — verifying your selfie in the background.")
                .selfieUrl(savedSelfieUrl != null ? savedSelfieUrl : profile.getSelfieUrl())
                .build());
    }

    /**
     * Dedicated 1:1 Biometric Face Match between live selfie and uploaded profile photos.
     */
    @PostMapping("/verify-face-match")
    public ResponseEntity<KycDto.FaceMatchResponse> verifyFaceMatch(
            @AuthenticationPrincipal UUID userId,
            @RequestBody KycDto.FaceMatchRequest request) {

        if (request == null || request.getSelfieBase64() == null || request.getSelfieBase64().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(KycDto.FaceMatchResponse.builder()
                    .verified(false)
                    .status("MISSING_SELFIE")
                    .message("Live selfie camera capture is required for face verification.")
                    .build());
        }

        com.match.SwipeAI.model.Profile profile = profileRepository.findById(userId).orElse(null);
        String primaryPhoto = null;
        if (request.getProfilePhotoUrl() != null && !request.getProfilePhotoUrl().trim().isEmpty()) {
            primaryPhoto = request.getProfilePhotoUrl().trim();
        } else if (profile != null) {
            if (profile.getPhoto1() != null && !profile.getPhoto1().trim().isEmpty()) {
                primaryPhoto = profile.getPhoto1().trim();
            } else if (profile.getPhoto2() != null && !profile.getPhoto2().trim().isEmpty()) {
                primaryPhoto = profile.getPhoto2().trim();
            }
        }

        if (primaryPhoto == null) {
            return ResponseEntity.badRequest().body(KycDto.FaceMatchResponse.builder()
                    .verified(false)
                    .status("NO_PROFILE_PHOTO")
                    .message("Please upload at least one profile photo first so we can verify your identity.")
                    .build());
        }

        FaceMatchService.FaceMatchResult result = faceMatchService.compareFaces(
                request.getSelfieBase64(), primaryPhoto
        );

        if (result.isMatch()) {
            String savedSelfieUrl = null;
            try {
                String cleanBase64 = request.getSelfieBase64().replaceAll("^data:image/[a-zA-Z]+;base64,", "");
                byte[] selfieBytes = java.util.Base64.getDecoder().decode(cleanBase64);
                var uploadRes = r2StorageService.uploadFile("selfie_" + userId + ".jpg", "image/jpeg", selfieBytes);
                savedSelfieUrl = uploadRes.get("publicUrl");
            } catch (Exception ignored) {}

            final String finalSelfieUrl = savedSelfieUrl;
            userRepository.findById(userId).ifPresent(user -> {
                user.setFaceVerified(true);
                user.setLivenessScore(result.getSimilarityScore());
                userRepository.save(user);
            });

            if (profile != null) {
                if (finalSelfieUrl != null) {
                    profile.setSelfieUrl(finalSelfieUrl);
                }
                profileRepository.save(profile);
            }

            return ResponseEntity.ok(KycDto.FaceMatchResponse.builder()
                    .verified(true)
                    .similarityScore(result.getSimilarityScore())
                    .confidencePercent(result.getConfidencePercent())
                    .selfieFacesDetected(result.getSelfieFacesDetected())
                    .photoFacesDetected(result.getPhotoFacesDetected())
                    .status(result.getStatus())
                    .message(result.getMessage())
                    .selfieUrl(savedSelfieUrl != null ? savedSelfieUrl : (profile != null ? profile.getSelfieUrl() : null))
                    .build());
        } else {
            userRepository.findById(userId).ifPresent(user -> {
                user.setFaceVerified(false);
                user.setLivenessScore(0.0);
                userRepository.save(user);
            });

            return ResponseEntity.status(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY).body(
                    KycDto.FaceMatchResponse.builder()
                            .verified(false)
                            .similarityScore(result.getSimilarityScore())
                            .confidencePercent(result.getConfidencePercent())
                            .selfieFacesDetected(result.getSelfieFacesDetected())
                            .photoFacesDetected(result.getPhotoFacesDetected())
                            .status(result.getStatus())
                            .message(result.getMessage())
                            .build()
            );
        }
    }
}
