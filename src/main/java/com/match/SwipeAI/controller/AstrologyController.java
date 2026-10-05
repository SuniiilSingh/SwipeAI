package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.AstrologyDto;
import com.match.SwipeAI.model.UserAstrology;
import com.match.SwipeAI.service.VedicAstrologyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@CrossOrigin
@RestController
@RequestMapping("/v1/astrology")
@RequiredArgsConstructor
public class AstrologyController {

    private final VedicAstrologyService astrologyService;

    @GetMapping("/my-chart")
    public ResponseEntity<AstrologyDto.UserAstrologyResponse> getMyChart(@AuthenticationPrincipal UUID userId) {
        UserAstrology a = astrologyService.getOrComputeAstrology(userId);
        return ResponseEntity.ok(toResponse(a));
    }

    @PutMapping("/birth-details")
    public ResponseEntity<AstrologyDto.UserAstrologyResponse> updateBirthDetails(
            @AuthenticationPrincipal UUID userId,
            @RequestBody AstrologyDto.UpdateBirthDetailsRequest request) {
        UserAstrology a = astrologyService.updateBirthDetails(userId, request);
        return ResponseEntity.ok(toResponse(a));
    }

    @GetMapping("/match/{targetUserId}")
    public ResponseEntity<AstrologyDto.AshtakootMatchResponse> getAstrologyMatch(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID targetUserId) {
        AstrologyDto.AshtakootMatchResponse match = astrologyService.calculateAshtakootMatch(userId, targetUserId);
        return ResponseEntity.ok(match);
    }

    private AstrologyDto.UserAstrologyResponse toResponse(UserAstrology a) {
        return AstrologyDto.UserAstrologyResponse.builder()
                .userId(a.getUserId())
                .birthTime(a.getBirthTime())
                .birthCity(a.getBirthCity())
                .isExactTimeProvided(a.getIsExactTimeProvided())
                .nakshatraId(a.getNakshatraId())
                .nakshatraName(a.getNakshatraName())
                .nakshatraPada(a.getNakshatraPada())
                .chandraRashi(a.getChandraRashi())
                .chandraRashiLord(a.getChandraRashiLord())
                .sunSign(a.getSunSign())
                .lagnaSign(a.getLagnaSign())
                .varna(a.getVarna())
                .vashya(a.getVashya())
                .yoniAnimal(a.getYoniAnimal())
                .gana(a.getGana())
                .nadi(a.getNadi())
                .numerologyNumber(a.getNumerologyNumber())
                .isManglik(a.getIsManglik())
                .build();
    }
}
