package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<SupportTicket> findByTicketNumber(String ticketNumber);
    void deleteByUserId(UUID userId);
}
