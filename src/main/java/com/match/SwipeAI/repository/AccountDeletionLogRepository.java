package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.AccountDeletionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccountDeletionLogRepository extends JpaRepository<AccountDeletionLog, UUID> {
    List<AccountDeletionLog> findAllByOrderByCreatedAtDesc();
    List<AccountDeletionLog> findByPhoneE164ContainingIgnoreCaseOrderByCreatedAtDesc(String phone);
    List<AccountDeletionLog> findByReasonCategoryOrderByCreatedAtDesc(String reasonCategory);
}
