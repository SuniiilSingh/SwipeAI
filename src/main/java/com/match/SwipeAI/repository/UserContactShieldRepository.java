package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.UserContactShield;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserContactShieldRepository extends JpaRepository<UserContactShield, Long> {
    List<UserContactShield> findByUserId(UUID userId);
    List<UserContactShield> findByContactPhoneHash(String contactPhoneHash);
    List<UserContactShield> findByCorporateDomain(String corporateDomain);
    Optional<UserContactShield> findByUserIdAndCorporateDomainIsNotNull(UUID userId);
    void deleteByUserId(UUID userId);
}
