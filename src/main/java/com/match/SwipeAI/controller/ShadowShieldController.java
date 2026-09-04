package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.ShieldDto;
import com.match.SwipeAI.model.UserContactShield;
import com.match.SwipeAI.repository.UserContactShieldRepository;
import com.match.SwipeAI.service.engine.ShadowShieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Relative & Boss Auto-Shield (Shadow Shield) Controller.
 * Ingests salted SHA-256 phonebook hashes and corporate email domains
 * to ensure 100% bi-directional invisibility from family, relatives, neighbors, and coworkers.
 */
@RestController
@RequestMapping("/v1/privacy/shadow-shield")
@RequiredArgsConstructor
public class ShadowShieldController {

    private final ShadowShieldService shadowShieldService;
    private final UserContactShieldRepository shieldRepository;

    /**
     * Ingest client-hashed or raw phone numbers to salt and store for Zero-Knowledge matching.
     *
     * @param userId Authenticated user UUID
     * @param request Contact phone hashes or raw phone numbers
     * @return Updated shield status with active count
     */
    @PostMapping("/sync-contacts")
    public ResponseEntity<ShieldDto.ShieldStatusResponse> syncContacts(
            @AuthenticationPrincipal UUID userId,
            @RequestBody ShieldDto.SyncContactsRequest request) {
        ShieldDto.ShieldStatusResponse response = shadowShieldService.syncContacts(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Set corporate email domain blocker (e.g. swiggy.in, tcs.com, infosys.com).
     *
     * @param userId Authenticated user UUID
     * @param request Contains corporate domain string
     * @return Confirmation that coworkers with this domain are shielded
     */
    @PostMapping("/domain")
    public ResponseEntity<ShieldDto.ShieldStatusResponse> setCorporateDomain(
            @AuthenticationPrincipal UUID userId,
            @RequestBody ShieldDto.DomainShieldRequest request) {
        ShieldDto.ShieldStatusResponse response = shadowShieldService.setCorporateDomain(userId, request.getCorporateDomain());
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve the current Shadow Shield configuration and active count for the user.
     *
     * @param userId Authenticated user UUID
     * @return Shield status
     */
    @GetMapping("/status")
    public ResponseEntity<ShieldDto.ShieldStatusResponse> getStatus(@AuthenticationPrincipal UUID userId) {
        List<UserContactShield> shields = shieldRepository.findByUserId(userId);
        String domain = null;
        for (UserContactShield s : shields) {
            if (s.getCorporateDomain() != null) {
                domain = s.getCorporateDomain();
                break;
            }
        }

        return ResponseEntity.ok(ShieldDto.ShieldStatusResponse.builder()
                .shieldedContactsCount(shields.size())
                .corporateDomain(domain)
                .isShieldActive(!shields.isEmpty())
                .message("Shadow Shield is Active. Zero bi-directional visibility with family, relatives & coworkers.")
                .build());
    }

    /**
     * Clear all shielded contacts for the user.
     */
    @DeleteMapping("/contacts")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<java.util.Map<String, String>> clearContacts(@AuthenticationPrincipal UUID userId) {
        shieldRepository.deleteByUserId(userId);
        return ResponseEntity.ok(java.util.Map.of("status", "success", "message", "Shielded contacts cleared successfully."));
    }
}
