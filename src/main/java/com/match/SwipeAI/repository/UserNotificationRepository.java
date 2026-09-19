package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.UserNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {

    List<UserNotification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<UserNotification> findByUserIdAndIsReadOrderByCreatedAtDesc(UUID userId, boolean isRead, Pageable pageable);

    long countByUserIdAndIsReadFalse(UUID userId);

    Optional<UserNotification> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE UserNotification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE UserNotification n SET n.isRead = true WHERE n.id = :id AND n.userId = :userId")
    int markAsRead(@Param("id") UUID id, @Param("userId") UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);
}
