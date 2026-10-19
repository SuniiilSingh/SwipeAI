package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.UserReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserReportRepository extends JpaRepository<UserReport, UUID> {

    List<UserReport> findByReportedUserIdOrderByCreatedAtDesc(UUID reportedUserId);

    List<UserReport> findByReporterIdOrderByCreatedAtDesc(UUID reporterId);

    List<UserReport> findByStatusOrderByCreatedAtDesc(String status);

    long countByReportedUserId(UUID reportedUserId);

    long countByReportedUserIdAndStatus(UUID reportedUserId, String status);

    @Query("SELECT r.reportedUserId, count(r.id) FROM UserReport r WHERE r.status = :status GROUP BY r.reportedUserId ORDER BY count(r.id) DESC")
    List<Object[]> countReportsGroupedByReportedUser(String status);

    @Query("SELECT r.reportedUserId, count(r.id) FROM UserReport r GROUP BY r.reportedUserId ORDER BY count(r.id) DESC")
    List<Object[]> countAllReportsGroupedByReportedUser();
}
