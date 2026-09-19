package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.DesireDto;
import com.match.SwipeAI.service.DesireProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for managing the user's Desire Profile (Partner Blueprint).
 */
@RestController
@RequestMapping("/v1/profiles/desire")
@RequiredArgsConstructor
public class DesireProfileController {

    private final DesireProfileService desireProfileService;

    @GetMapping
    public ResponseEntity<DesireDto.DesireProfileResponse> getMyDesireProfile(
            @AuthenticationPrincipal UUID userId) {
        DesireDto.DesireProfileResponse response = desireProfileService.getDesireProfile(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<DesireDto.DesireProfileResponse> updateMyDesireProfile(
            @AuthenticationPrincipal UUID userId,
            @RequestBody DesireDto.DesireProfileRequest request) {
        DesireDto.DesireProfileResponse response = desireProfileService.saveDesireProfile(userId, request);
        return ResponseEntity.ok(response);
    }
}
