package com.match.SwipeAI.dto;

import com.match.SwipeAI.enums.TicketCategory;
import com.match.SwipeAI.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

public class SupportDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateTicketRequest {
        private TicketCategory category;
        private String subject;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolveTicketRequest {
        private String resolutionNotes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketResponse {
        private UUID id;
        private String ticketNumber;
        private TicketCategory category;
        private TicketStatus status;
        private String subject;
        private String description;
        private String resolutionNotes;
        private OffsetDateTime createdAt;
        private OffsetDateTime resolvedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FaqItemDto {
        private String id;
        private String question;
        private String answer;
        private String category;
    }
}
