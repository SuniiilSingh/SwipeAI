package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.SafeDateSpot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SafeDateSpotRepository extends JpaRepository<SafeDateSpot, Long> {
    List<SafeDateSpot> findByCityIgnoreCase(String city);
    List<SafeDateSpot> findTop50ByCityIgnoreCaseOrderByNameAsc(String city);
    List<SafeDateSpot> findTop50ByOrderByCityAscNameAsc();
}
