package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByMatchIdOrderByCreatedAtAsc(UUID matchId);
    long countByMatchId(UUID matchId);
    void deleteByMatchId(UUID matchId);
}
