package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.MatchDto;
import com.match.SwipeAI.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Match Lifecycle and Pre-Chat Icebreaker Controller.
 * Handles 48-hour ephemeral countdown timers, 10-second rapid fire quiz answers,
 * and AI Wingman conversational spark generation.
 */
@CrossOrigin
@RestController
@RequestMapping("/v1/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    /**
     * Fetch all active matches for the authenticated user with remaining timer hours.
     *
     * @param userId Authenticated user UUID
     * @return List of active matches
     */
    @GetMapping
    public ResponseEntity<List<MatchDto.MatchResponseDto>> getMatches(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, Math.min(size, 100));
        List<MatchDto.MatchResponseDto> matches = matchService.getMatchesForUser(userId, pageable);
        return ResponseEntity.ok(matches);
    }

    /**
     * Retrieve match details and icebreaker quiz state for a specific match.
     *
     * @param userId Authenticated user UUID
     * @param id Match UUID
     * @return Match details
     */
    @GetMapping("/{id}")
    public ResponseEntity<MatchDto.MatchResponseDto> getMatchDetails(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        MatchDto.MatchResponseDto match = matchService.getMatchDetails(id, userId);
        return ResponseEntity.ok(match);
    }

    /**
     * Submit user's answer to the 10-second rapid-fire icebreaker quiz.
     * When both users complete the quiz, transitions match status to ACTIVE_CHAT.
     *
     * @param userId Authenticated user UUID
     * @param id Match UUID
     * @param request Selected option index
     * @return Quiz completion status and AI Wingman suggestions
     */
    @PostMapping("/{id}/icebreaker/answer")
    public ResponseEntity<MatchDto.IcebreakerAnswerResponse> answerIcebreaker(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestBody MatchDto.IcebreakerAnswerRequest request) {
        MatchDto.IcebreakerAnswerResponse response = matchService.answerIcebreaker(id, userId, request.getSelectedOptionIndex());
        return ResponseEntity.ok(response);
    }

    /**
     * Request 3 personalized, witty Hinglish/English opening sparks from AI Wingman.
     *
     * @param userId Authenticated user UUID
     * @param id Match UUID
     * @return List of conversational sparks
     */
    @GetMapping("/{id}/wingman/sparks")
    public ResponseEntity<MatchDto.WingmanSparksResponse> getWingmanSparks(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        MatchDto.WingmanSparksResponse response = matchService.getWingmanSparks(id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Unmatch and permanently sever messaging and WebRTC connections.
     *
     * @param userId Authenticated user UUID
     * @param id Match UUID
     * @return Success confirmation
     */
    @PostMapping("/{id}/unmatch")
    public ResponseEntity<Map<String, String>> unmatch(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        matchService.unmatch(id, userId);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Unmatched successfully."));
    }

    /**
     * Report a match for harassment, offensive messages, or fake profile.
     * Applies safety karma penalty, sets status to UNMATCHED, and logs SupportTicket for Admin Review.
     */
    @PostMapping("/{id}/report")
    public ResponseEntity<Map<String, String>> reportMatch(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null && body.containsKey("reason") ? body.get("reason") : "Inappropriate behavior";
        String remarks = body != null && body.containsKey("remarks") ? body.get("remarks") : null;
        matchService.reportMatch(id, userId, reason, remarks);
        return ResponseEntity.ok(Map.of("status", "success", "message", "User reported and blocked successfully."));
    }

    /**
     * Block a match from chat lounge.
     * Sets status to UNMATCHED and logs SupportTicket with remarks for Admin Review.
     */
    @PostMapping("/{id}/block")
    public ResponseEntity<Map<String, String>> blockMatch(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null && body.containsKey("reason") ? body.get("reason") : "Direct Block";
        String remarks = body != null && body.containsKey("remarks") ? body.get("remarks") : null;
        matchService.blockMatch(id, userId, reason, remarks);
        return ResponseEntity.ok(Map.of("status", "success", "message", "User blocked successfully."));
    }

    /**
     * Delete/unmatch a match by ID (RESTful Delete).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteMatch(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        matchService.unmatch(id, userId);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Match deleted successfully."));
    }

    /**
     * Get user's Rule of 5 active match capacity status.
     */
    @GetMapping("/capacity")
    public ResponseEntity<MatchDto.MatchCapacityDto> getMatchCapacity(@AuthenticationPrincipal UUID userId) {
        MatchDto.MatchCapacityDto capacity = matchService.getMatchCapacity(userId);
        return ResponseEntity.ok(capacity);
    }

    /**
     * Mark a real date as planned or numbers exchanged.
     * Archives the conversation to Date Book and frees up 1 of the 5 active slots.
     */
    @PostMapping("/{id}/plan-date")
    public ResponseEntity<MatchDto.MatchResponseDto> planDate(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestBody(required = false) MatchDto.DatePlanRequest request) {
        String venue = request != null ? request.getVenueName() : null;
        String note = request != null ? request.getNote() : null;
        MatchDto.MatchResponseDto res = matchService.markDatePlanned(id, userId, venue, note);
        return ResponseEntity.ok(res);
    }

    /**
     * Cancel a planned date with respect.
     * Reverts match status to ACTIVE_CHAT, notifies partner, and preserves Rule of 5 slot without negative karma.
     */
    @PostMapping("/{id}/cancel-date")
    public ResponseEntity<MatchDto.MatchResponseDto> cancelDate(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestBody(required = false) MatchDto.DatePlanRequest request) {
        String note = request != null ? request.getNote() : "Date cancelled with respect";
        MatchDto.MatchResponseDto res = matchService.cancelPlannedDate(id, userId, note);
        return ResponseEntity.ok(res);
    }

    /**
     * Graceful exit: send polite farewell and close chat to free an active slot.
     */
    @PostMapping("/{id}/graceful-exit")
    public ResponseEntity<Map<String, String>> gracefulExit(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestBody(required = false) MatchDto.DatePlanRequest request) {
        String note = request != null ? request.getNote() : "Polite farewell";
        matchService.gracefulExit(id, userId, note);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Graceful exit completed. Active slot freed under Rule of 5!"));
    }

    /**
     * Send polite farewell, archive match, and free active slot under Rule of 5.
     */
    @PostMapping("/{id}/farewell")
    public ResponseEntity<Map<String, String>> farewellMatch(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String note = body != null && body.containsKey("note") ? body.get("note") : "Polite farewell";
        matchService.gracefulExit(id, userId, note);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Polite farewell sent. Active slot freed under Rule of 5!"));
    }

    /**
     * Revive an expired match or extend countdown window by +24 hours.
     */
    @PostMapping("/{id}/revive")
    public ResponseEntity<MatchDto.MatchResponseDto> reviveMatch(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        MatchDto.MatchResponseDto res = matchService.reviveMatch(id, userId);
        return ResponseEntity.ok(res);
    }

    /**
     * Specifically unlock a queued match when a slot has been freed under the Rule of 5.
     */
    @PostMapping("/{id}/unlock-queued")
    public ResponseEntity<MatchDto.MatchResponseDto> unlockQueued(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        MatchDto.MatchResponseDto res = matchService.unlockSpecificQueuedMatch(id, userId);
        return ResponseEntity.ok(res);
    }
}

