package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhoneE164(String phoneE164);
    Optional<User> findByDigilockerSubHash(String digilockerSubHash);
}
