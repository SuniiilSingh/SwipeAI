package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    List<Profile> findByMicroCircle(String microCircle);
    List<Profile> findByCity(String city);
}
