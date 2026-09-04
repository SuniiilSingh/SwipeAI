package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.ProfileDto;
import com.match.SwipeAI.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Profile and Cultural Canvas Controller.
 * Manages dietary preferences, living status, vernacular voice prompts,
 * Meme DNA daily swipes, and Cosmic Chemistry 2.0 vibe cards.
 */
@RestController
@RequestMapping("/v1/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /**
     * Fetch full cultural profile of the authenticated user.
     *
     * @param userId Authenticated user UUID
     * @return Profile response with badges and balances
     */
    @GetMapping("/me")
    public ResponseEntity<ProfileDto.ProfileResponse> getMyProfile(@AuthenticationPrincipal UUID userId) {
        ProfileDto.ProfileResponse response = profileService.getProfile(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Update cultural context, living situation, diet, or photos.
     *
     * @param userId Authenticated user UUID
     * @param request Updated profile fields
     * @return Updated profile response
     */
    @PutMapping("/me")
    public ResponseEntity<ProfileDto.ProfileResponse> updateMyProfile(
            @AuthenticationPrincipal UUID userId,
            @RequestBody ProfileDto.ProfileRequest request) {
        ProfileDto.ProfileResponse response = profileService.updateProfile(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Upload or update a vernacular Hinglish/regional voice prompt snippet.
     *
     * @param userId Authenticated user UUID
     * @param request Voice prompt URL, duration in seconds, and transcript
     * @return Updated profile
     */
    @PostMapping("/voice-prompt")
    public ResponseEntity<ProfileDto.ProfileResponse> uploadVoicePrompt(
            @AuthenticationPrincipal UUID userId,
            @RequestBody ProfileDto.VoicePromptUploadRequest request) {
        ProfileDto.ProfileResponse response = profileService.updateVoicePrompt(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Record a meme swipe for the 5-meme daily humor calibration mini-game.
     *
     * @param userId Authenticated user UUID
     * @param request Meme ID and like/pass boolean
     * @return Confirmation
     */
    @PostMapping("/meme-dna/swipe")
    public ResponseEntity<Map<String, String>> recordMemeSwipe(
            @AuthenticationPrincipal UUID userId,
            @RequestBody ProfileDto.MemeSwipeRequest request) {
        profileService.recordMemeSwipe(userId, request);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Meme DNA profile updated."));
    }

    /**
     * Generate modern Cosmic Chemistry 2.0 astrological synergy report and vibe card.
     *
     * @param userId Authenticated user UUID
     * @param targetUserId Candidate user UUID
     * @return Synastry report and conversational spark
     */
    @GetMapping("/cosmic-chemistry/{targetUserId}")
    public ResponseEntity<ProfileDto.CosmicChemistryResponse> getCosmicChemistry(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID targetUserId) {
        ProfileDto.CosmicChemistryResponse response = profileService.getCosmicChemistry(userId, targetUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Fetch public profile view by user ID.
     *
     * @param id Target user UUID
     * @return Public profile details
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProfileDto.ProfileResponse> getProfileById(@PathVariable UUID id) {
        ProfileDto.ProfileResponse response = profileService.getProfile(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete user account and profile data (Right to Erasure / DPDP compliance).
     *
     * @param userId Authenticated user UUID
     * @return Confirmation
     */
    @DeleteMapping("/me")
    public ResponseEntity<Map<String, String>> deleteMyProfile(@AuthenticationPrincipal UUID userId) {
        profileService.deleteProfile(userId);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Account and profile deleted successfully."));
    }
}
