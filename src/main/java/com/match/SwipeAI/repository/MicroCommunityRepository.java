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

    @org.springframework.data.jpa.repository.Query("SELECT m FROM MicroCommunity m WHERE LOWER(m.city) IN :cities ORDER BY m.isPopular DESC, m.name ASC")
    List<MicroCommunity> findByCitiesIgnoreCase(@org.springframework.data.repository.query.Param("cities") List<String> cities);
}
