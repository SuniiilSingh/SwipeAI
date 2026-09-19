package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PushTokenRepository extends JpaRepository<PushToken, UUID> {
    List<PushToken> findByUserId(UUID userId);
    Optional<PushToken> findByUserIdAndToken(UUID userId, String token);
    void deleteByToken(String token);
    void deleteByUserIdAndToken(UUID userId, String token);
    void deleteByUserId(UUID userId);
}
