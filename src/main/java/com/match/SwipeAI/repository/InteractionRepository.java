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
    List<Interaction> findByActorId(UUID actorId);
    long countByActorIdAndCreatedAtAfter(UUID actorId, OffsetDateTime since);
}
