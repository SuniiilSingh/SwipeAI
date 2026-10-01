package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.SupportDto;
import com.match.SwipeAI.service.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    @PostMapping("/tickets")
    public ResponseEntity<SupportDto.TicketResponse> createTicket(
            @AuthenticationPrincipal UUID userId,
            @RequestBody SupportDto.CreateTicketRequest request) {
        SupportDto.TicketResponse response = supportService.createTicket(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tickets")
    public ResponseEntity<List<SupportDto.TicketResponse>> getUserTickets(
            @AuthenticationPrincipal UUID userId) {
        List<SupportDto.TicketResponse> tickets = supportService.getUserTickets(userId);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/faqs")
    public ResponseEntity<List<SupportDto.FaqItemDto>> getFaqs() {
        return ResponseEntity.ok(supportService.getFaqs());
    }

    @PostMapping("/tickets/{id}/resolve")
    public ResponseEntity<SupportDto.TicketResponse> resolveTicket(
            @PathVariable UUID id,
            @RequestBody(required = false) SupportDto.ResolveTicketRequest request) {
        String notes = request != null ? request.getResolutionNotes() : null;
        SupportDto.TicketResponse response = supportService.resolveTicket(id, notes);
        return ResponseEntity.ok(response);
    }
}
