package com.match.SwipeAI.repository;

import com.match.SwipeAI.enums.OrderStatus;
import com.match.SwipeAI.model.UpiOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access repository for UPI micro-transactions.
 */
@Repository
public interface UpiOrderRepository extends JpaRepository<UpiOrder, UUID> {
    Optional<UpiOrder> findByOrderId(String orderId);
    List<UpiOrder> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<UpiOrder> findByUserIdAndStatus(UUID userId, OrderStatus status);
}
