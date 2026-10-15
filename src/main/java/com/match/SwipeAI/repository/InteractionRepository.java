package com.match.SwipeAI.repository;

import com.match.SwipeAI.enums.ActionType;
import com.match.SwipeAI.model.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access repository for high-intent interactions (likes, passes, super chai).
 */
@Repository
public interface InteractionRepository extends JpaRepository<Interaction, Long> {
    Optional<Interaction> findByActorIdAndTargetId(UUID actorId, UUID targetId);
    List<Interaction> findByTargetIdAndActionType(UUID targetId, ActionType actionType);
    List<Interaction> findByTargetIdAndActionTypeInOrderByCreatedAtDesc(UUID targetId, java.util.Collection<ActionType> actionTypes);
    List<Interaction> findByActorId(UUID actorId);
    long countByActorIdAndCreatedAtAfter(UUID actorId, OffsetDateTime since);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(i) FROM Interaction i WHERE i.actorId = :actorId AND i.commentText IS NOT NULL AND TRIM(i.commentText) <> '' AND i.createdAt >= :since")
    long countSparkNotesUsedSince(@org.springframework.data.repository.query.Param("actorId") UUID actorId, @org.springframework.data.repository.query.Param("since") OffsetDateTime since);
}
