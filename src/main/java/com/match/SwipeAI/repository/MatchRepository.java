package com.match.SwipeAI.repository;

import com.match.SwipeAI.enums.MatchStatus;
import com.match.SwipeAI.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access repository for state machine matches.
 */
@Repository
public interface MatchRepository extends JpaRepository<Match, UUID> {

    @Query("SELECT m FROM Match m WHERE (m.userAId = :userId OR m.userBId = :userId) AND m.status != 'UNMATCHED' ORDER BY m.matchedAt DESC")
    List<Match> findActiveMatchesForUser(@Param("userId") UUID userId);

    @Query("SELECT m FROM Match m WHERE ((m.userAId = :userA AND m.userBId = :userB) OR (m.userAId = :userB AND m.userBId = :userA))")
    Optional<Match> findMatchBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);

    List<Match> findByStatusAndExpiresAtBefore(MatchStatus status, OffsetDateTime time);
}
