package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.CallingDto;
import com.match.SwipeAI.model.Profile;
import com.match.SwipeAI.repository.ProfileRepository;
import com.match.SwipeAI.service.integration.LiveKitCallingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Masked Audio & Video Calling (Virtual Chai) Controller.
 * Provides encrypted WebRTC calling tokens without exposing phone numbers or WhatsApp contacts.
 */
@RestController
@RequestMapping("/v1/calling")
@RequiredArgsConstructor
public class CallingController {

    private final LiveKitCallingService liveKitCallingService;
    private final ProfileRepository profileRepository;

    /**
     * Create a masked "Virtual Chai" WebRTC room session.
     *
     * @param userId Authenticated caller UUID
     * @param request Match ID and audio/video mode flag
     * @return LiveKit room name, participant token, and masked caller identity
     */
    @PostMapping("/virtual-chai/session")
    public ResponseEntity<CallingDto.VirtualChaiSessionResponse> createVirtualChaiSession(
            @AuthenticationPrincipal UUID userId,
            @RequestBody CallingDto.VirtualChaiSessionRequest request) {
        Profile profile = profileRepository.findById(userId).orElse(null);
        String myName = profile != null ? profile.getDisplayName() : "Caller";

        CallingDto.VirtualChaiSessionResponse response = liveKitCallingService.createCallingSession(
                request.getMatchId(),
                userId,
                myName,
                "Match Partner"
        );
        return ResponseEntity.ok(response);
    }
}
