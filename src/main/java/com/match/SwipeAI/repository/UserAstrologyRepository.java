package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.UserAstrology;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserAstrologyRepository extends JpaRepository<UserAstrology, UUID> {
}
