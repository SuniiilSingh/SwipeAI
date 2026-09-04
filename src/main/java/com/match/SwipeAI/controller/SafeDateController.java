package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.SafeDateDto;
import com.match.SwipeAI.service.SafeDateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Safe Date Spots O2O and Live SOS Controller.
 * Provides curated partner cafes (Blue Tokai, Third Wave, Starbucks) with 15% discount codes
 * and triggers discrete emergency contact tracking links upon check-in.
 */
@RestController
@RequestMapping("/v1/safe-date")
@RequiredArgsConstructor
public class SafeDateController {

    private final SafeDateService safeDateService;

    /**
     * Fetch verified partner safe date cafes for a given city.
     *
     * @param city City filter (default: Bengaluru)
     * @return List of safe spots with discount coupons and geofence data
     */
    @GetMapping("/spots")
    public ResponseEntity<List<SafeDateDto.SafeDateSpotDto>> getSpots(@RequestParam(required = false) String city) {
        List<SafeDateDto.SafeDateSpotDto> spots = safeDateService.getSafeSpots(city);
        return ResponseEntity.ok(spots);
    }

    /**
     * Start a Safe Date check-in session and generate an emergency contact tracking link.
     *
     * @param userId Authenticated user UUID
     * @param request Cafe spot ID, match ID, and emergency contact phone numbers
     * @return SOS tracking session details and active link
     */
    @PostMapping("/sos/start")
    public ResponseEntity<SafeDateDto.SosStartResponse> startSos(
            @AuthenticationPrincipal UUID userId,
            @RequestBody SafeDateDto.SosStartRequest request) {
        SafeDateDto.SosStartResponse response = safeDateService.startSosSession(userId, request);
        return ResponseEntity.ok(response);
    }
}
