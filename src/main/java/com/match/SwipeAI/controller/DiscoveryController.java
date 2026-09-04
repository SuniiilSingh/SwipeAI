package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.DiscoveryDto;
import com.match.SwipeAI.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Discovery Feed and High-Intent Recommendation Controller.
 * Enforces the 25 profile/day anti-fatigue hard cap, applies Shadow Shield contact filtering,
 * and ranks candidates using the multi-objective formula.
 */
@RestController
@RequestMapping("/v1/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    /**
     * Fetch the daily capped discovery feed for the viewing user.
     *
     * @param userId Authenticated user UUID
     * @param request Filters for distance, dietary preferences, intent, and micro-circles
     * @return Ranked candidate cards and remaining daily swipes
     */
    @PostMapping("/feed")
    public ResponseEntity<DiscoveryDto.DiscoveryFeedResponse> getFeed(
            @AuthenticationPrincipal UUID userId,
            @RequestBody(required = false) DiscoveryDto.DiscoveryFeedRequest request) {
        if (request == null) {
            request = new DiscoveryDto.DiscoveryFeedRequest();
        }
        DiscoveryDto.DiscoveryFeedResponse response = discoveryService.getDiscoveryFeed(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Record a high-intent interaction (Like, Pass, or Super Chai) anchored to a prompt, photo, or voice note.
     * Automatically triggers a new Match in PENDING_ICEBREAKER state if reciprocal like exists.
     *
     * @param userId Authenticated user UUID
     * @param request Target ID, action type, context type, target ID, and comment
     * @return Match status and remaining daily swipes
     */
    @PostMapping("/interact")
    public ResponseEntity<DiscoveryDto.InteractionResponse> recordInteraction(
            @AuthenticationPrincipal UUID userId,
            @RequestBody DiscoveryDto.InteractionRequest request) {
        DiscoveryDto.InteractionResponse response = discoveryService.recordInteraction(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve available Micro-Community lifestyle circles (e.g. Koramangala Tech, DMRC Yellow Line).
     *
     * @return List of active micro-circles with member counts
     */
    @GetMapping("/circles")
    public ResponseEntity<List<DiscoveryDto.CircleDto>> getMicroCircles() {
        List<DiscoveryDto.CircleDto> circles = discoveryService.getMicroCircles();
        return ResponseEntity.ok(circles);
    }
}
