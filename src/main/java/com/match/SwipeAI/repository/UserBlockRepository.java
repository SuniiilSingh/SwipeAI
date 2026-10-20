package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {

    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);

    List<UserBlock> findByBlockedUserIdOrderByCreatedAtDesc(UUID blockedUserId);

    List<UserBlock> findAllByOrderByCreatedAtDesc();

    long countByBlockedUserId(UUID blockedUserId);

    Optional<UserBlock> findByBlockerIdAndBlockedUserId(UUID blockerId, UUID blockedUserId);

    boolean existsByBlockerIdAndBlockedUserId(UUID blockerId, UUID blockedUserId);

    void deleteByBlockerIdAndBlockedUserId(UUID blockerId, UUID blockedUserId);
}
