package com.match.SwipeAI.service;

import com.match.SwipeAI.dto.SupportDto;
import com.match.SwipeAI.enums.TicketCategory;
import com.match.SwipeAI.enums.TicketStatus;
import com.match.SwipeAI.model.SupportTicket;
import com.match.SwipeAI.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportTicketRepository supportTicketRepository;
    private final Random random = new Random();

    @Transactional
    public SupportDto.TicketResponse createTicket(UUID userId, SupportDto.CreateTicketRequest request) {
        if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
            throw new IllegalArgumentException("Subject is required.");
        }
        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Description is required.");
        }

        TicketCategory category = request.getCategory() != null ? request.getCategory() : TicketCategory.OTHER;
        String ticketNumber = "TKT-" + (10000 + random.nextInt(90000));

        SupportTicket ticket = SupportTicket.builder()
                .userId(userId)
                .ticketNumber(ticketNumber)
                .category(category)
                .status(TicketStatus.PENDING)
                .subject(request.getSubject().trim())
                .description(request.getDescription().trim())
                .build();

        ticket = supportTicketRepository.save(ticket);
        log.info("Created support ticket {} for user {}", ticketNumber, userId);
        return mapToDto(ticket);
    }

    public List<SupportDto.TicketResponse> getUserTickets(UUID userId) {
        return supportTicketRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SupportDto.TicketResponse resolveTicket(UUID ticketId, String resolutionNotes) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolvedAt(OffsetDateTime.now());
        ticket.setResolutionNotes(resolutionNotes != null && !resolutionNotes.isBlank()
                ? resolutionNotes
                : "Your concern has been thoroughly reviewed and resolved by our safety & support team. Thank you for your patience.");

        ticket = supportTicketRepository.save(ticket);
        log.info("Ticket {} resolved with notes: {}", ticket.getTicketNumber(), ticket.getResolutionNotes());
        return mapToDto(ticket);
    }

    public List<SupportDto.FaqItemDto> getFaqs() {
        return List.of(
                SupportDto.FaqItemDto.builder()
                        .id("faq-1")
                        .category("Matches & Chat")
                        .question("How does the 48-hour ephemeral timer work?")
                        .answer("When a match is formed, a 48-hour countdown timer begins. This prevents ghosting and keeps momentum active. Completing the 10s Icebreaker Quiz unlocks the chat lounge. Once you exchange 4+ messages, ghost-buster protection activates!")
                        .build(),
                SupportDto.FaqItemDto.builder()
                        .id("faq-2")
                        .category("Verification & Safety")
                        .question("How do I get DigiLocker and Liveness verified?")
                        .answer("Go to your Profile and tap 'DigiLocker Verification'. We use zero-knowledge proofs to verify your identity without storing your Aadhaar number. Complete the 3D selfie liveness check to earn the gold shield badge on your profile.")
                        .build(),
                SupportDto.FaqItemDto.builder()
                        .id("faq-3")
                        .category("Privacy & Shadow Shield")
                        .question("Can colleagues, exes, or family see my profile?")
                        .answer("No! Enable 'Shadow Shield' under Privacy in your Profile tab. You can sync your phone contacts or enter a corporate email domain (e.g., flipkart.com). We hash phone numbers using SHA-256 on your device, completely hiding you from those contacts.")
                        .build(),
                SupportDto.FaqItemDto.builder()
                        .id("faq-4")
                        .category("Plans & Billing")
                        .question("What perks are included with Blunderr Pass?")
                        .answer("Blunderr Pass unlocks Unlimited Daily Swipes, Rewind accidental passes, Super Sparks to highlight your profile, Direct Pre-Match Notes, and Priority Visibility. Remember: chat and messaging after matching is ALWAYS 100% free!")
                        .build(),
                SupportDto.FaqItemDto.builder()
                        .id("faq-5")
                        .category("Plans & Billing")
                        .question("What payment methods are supported?")
                        .answer("You can pay securely via all UPI apps (Google Pay, PhonePe, Paytm, BHIM), Credit/Debit Cards, NetBanking via Cashfree, or official Google Play / Apple StoreKit in-app billing.")
                        .build(),
                SupportDto.FaqItemDto.builder()
                        .id("faq-6")
                        .category("Safety & SOS")
                        .question("How does Safe Date & Live SOS check-in work?")
                        .answer("Under Profile > Safe Date, you can choose verified, well-lit partner cafes (like Blue Tokai & Third Wave) and start a live SOS session. A discrete tracking link is shared with your trusted emergency contact, plus you get a 15% cafe discount coupon.")
                        .build(),
                SupportDto.FaqItemDto.builder()
                        .id("faq-7")
                        .category("Account & Data")
                        .question("How do I delete my account and data?")
                        .answer("Under your Profile tab, scroll to the bottom and tap 'Delete Account'. Confirming will immediately wipe your profile, photos, matches, and messages in compliance with the India DPDP Act and App Store guidelines.")
                        .build()
        );
    }

    private SupportDto.TicketResponse mapToDto(SupportTicket ticket) {
        return SupportDto.TicketResponse.builder()
                .id(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .category(ticket.getCategory())
                .status(ticket.getStatus())
                .subject(ticket.getSubject())
                .description(ticket.getDescription())
                .resolutionNotes(ticket.getResolutionNotes())
                .createdAt(ticket.getCreatedAt())
                .resolvedAt(ticket.getResolvedAt())
                .build();
    }
}
