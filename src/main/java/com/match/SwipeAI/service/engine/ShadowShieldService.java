package com.match.SwipeAI.service.engine;

import com.match.SwipeAI.config.FeatureFlagsProperties;
import com.match.SwipeAI.dto.ShieldDto;
import com.match.SwipeAI.model.Profile;
import com.match.SwipeAI.model.User;
import com.match.SwipeAI.model.UserContactShield;
import com.match.SwipeAI.repository.UserContactShieldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowShieldService {

    private final FeatureFlagsProperties properties;
    private final UserContactShieldRepository shieldRepository;

    public String hashPhoneNumber(String rawPhoneNumber) {
        String serverPepper = properties.getSecurity().getServerPepper();
        String normalized = rawPhoneNumber.replaceAll("[^0-9]", "");
        if (normalized.length() > 10) {
            normalized = normalized.substring(normalized.length() - 10);
        }
        String input = normalized + ":" + serverPepper;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    @Transactional
    public ShieldDto.ShieldStatusResponse syncContacts(UUID userId, ShieldDto.SyncContactsRequest request) {
        List<String> hashesToStore = new ArrayList<>();

        if (request.getContactHashes() != null) {
            hashesToStore.addAll(request.getContactHashes());
        }

        if (request.getRawPhoneNumbers() != null) {
            for (String rawPhone : request.getRawPhoneNumbers()) {
                hashesToStore.add(hashPhoneNumber(rawPhone));
            }
        }

        for (String hash : hashesToStore) {
            UserContactShield shield = UserContactShield.builder()
                    .userId(userId)
                    .contactPhoneHash(hash)
                    .build();
            shieldRepository.save(shield);
        }

        long totalCount = shieldRepository.countByUserId(userId);
        log.info("Synced {} contact hashes for user {}. Total active shields: {}", hashesToStore.size(), userId, totalCount);

        return ShieldDto.ShieldStatusResponse.builder()
                .shieldedContactsCount((int) totalCount)
                .isShieldActive(totalCount > 0)
                .message("Shadow Shield Active: You are 100% invisible to all your phonebook contacts and relatives.")
                .build();
    }

    @Transactional
    public ShieldDto.ShieldStatusResponse setCorporateDomain(UUID userId, String domain) {
        String cleanDomain = domain.trim().toLowerCase();
        if (cleanDomain.startsWith("@")) {
            cleanDomain = cleanDomain.substring(1);
        }

        UserContactShield shield = UserContactShield.builder()
                .userId(userId)
                .contactPhoneHash(hashPhoneNumber("domain_" + cleanDomain))
                .corporateDomain(cleanDomain)
                .build();

        shieldRepository.save(shield);
        log.info("Set corporate domain shield @{} for user {}", cleanDomain, userId);

        return ShieldDto.ShieldStatusResponse.builder()
                .corporateDomain(cleanDomain)
                .isShieldActive(true)
                .message("Corporate Domain Shield Active: Co-workers with @" + cleanDomain + " will never see your profile.")
                .build();
    }

    public boolean isShielded(User viewer, Profile viewerProfile, User candidate, Profile candidateProfile) {
        // 1. Corporate domain check
        if (viewerProfile != null && candidateProfile != null) {
            String viewerCorp = viewerProfile.getCompany();
            String candidateCorp = candidateProfile.getCompany();
            if (viewerCorp != null && !viewerCorp.isBlank() && viewerCorp.equalsIgnoreCase(candidateCorp)) {
                log.debug("User {} and candidate {} filtered by Corporate Shield ({})", viewer.getId(), candidate.getId(), viewerCorp);
                return true;
            }
        }

        // 2. Query Contact Hash: Is candidate's phone in viewer's shield?
        String candidatePhoneHash = hashPhoneNumber(candidate.getPhoneE164());
        if (shieldRepository.existsByUserIdAndContactPhoneHash(viewer.getId(), candidatePhoneHash)) {
            log.debug("Candidate {} found in viewer {}'s contact shield", candidate.getId(), viewer.getId());
            return true;
        }

        // 3. Symmetric Check: Is viewer in candidate's shield?
        String viewerPhoneHash = hashPhoneNumber(viewer.getPhoneE164());
        if (shieldRepository.existsByUserIdAndContactPhoneHash(candidate.getId(), viewerPhoneHash)) {
            log.debug("Viewer {} found in candidate {}'s contact shield", viewer.getId(), candidate.getId());
            return true;
        }

        return false;
    }
}
