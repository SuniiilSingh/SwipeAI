package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.MicroCommunity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MicroCommunityRepository extends JpaRepository<MicroCommunity, UUID> {
    List<MicroCommunity> findByCityIgnoreCaseOrderByIsPopularDescNameAsc(String city);
    List<MicroCommunity> findAllByOrderByCityAscNameAsc();
    Optional<MicroCommunity> findBySlug(String slug);
    Optional<MicroCommunity> findByNameIgnoreCase(String name);
}
