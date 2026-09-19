package com.match.SwipeAI.repository;

import com.match.SwipeAI.enums.DietaryPreference;
import com.match.SwipeAI.enums.Gender;
import com.match.SwipeAI.model.Profile;
import com.match.SwipeAI.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.*;

/**
 * High-performance candidate search repository.
 * Pushes down spatial bounding boxes, gender matching, age boundaries,
 * interaction exclusions, and bounded limits directly to the database indexes,
 * eliminating full-table scans.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CandidateSearchRepository {

    @PersistenceContext
    private final EntityManager entityManager;

    public record CandidateUserRecord(User user, Profile profile) {}

    @Data
    @Builder
    public static class Criteria {
        private UUID viewerId;
        private Gender targetGender;
        private List<String> targetGenderDisplays;
        private Double minLat;
        private Double maxLat;
        private Double minLon;
        private Double maxLon;
        private LocalDate minBirthDate;
        private LocalDate maxBirthDate;
        private String microCircle;
        private List<DietaryPreference> dietaryPreferences;
        private int limit;
    }

    public List<CandidateUserRecord> searchCandidates(Criteria criteria) {
        StringBuilder jpql = new StringBuilder();
        jpql.append("SELECT u, p FROM User u LEFT JOIN Profile p ON p.userId = u.id WHERE 1=1 ");

        Map<String, Object> params = new HashMap<>();

        // 1. Exclude self
        if (criteria.getViewerId() != null) {
            jpql.append("AND u.id != :viewerId ");
            params.put("viewerId", criteria.getViewerId());

            // 2. Exclude already-interacted users directly via database subquery with index lookup
            jpql.append("AND NOT EXISTS (SELECT 1 FROM Interaction i WHERE i.actorId = :viewerId AND i.targetId = u.id) ");
        }

        // 3. Exclude incognito users
        jpql.append("AND (u.isIncognito IS NULL OR u.isIncognito = false) ");

        // 4. Filter by target gender if specified (utilizes idx_users_discovery / idx_users_active_gender_age)
        if (criteria.getTargetGender() != null) {
            if (criteria.getTargetGenderDisplays() != null && !criteria.getTargetGenderDisplays().isEmpty()) {
                jpql.append("AND (u.gender = :targetGender OR LOWER(p.genderDisplay) IN :genderDisplays) ");
                params.put("targetGender", criteria.getTargetGender());
                params.put("genderDisplays", criteria.getTargetGenderDisplays().stream().map(String::toLowerCase).toList());
            } else {
                jpql.append("AND u.gender = :targetGender ");
                params.put("targetGender", criteria.getTargetGender());
            }
        }

        // 5. Spatial bounding box pushdown (utilizes idx_users_lat_lng)
        if (criteria.getMinLat() != null && criteria.getMaxLat() != null &&
            criteria.getMinLon() != null && criteria.getMaxLon() != null) {
            jpql.append("AND (u.latitude IS NULL OR (u.latitude BETWEEN :minLat AND :maxLat AND u.longitude BETWEEN :minLon AND :maxLon)) ");
            params.put("minLat", criteria.getMinLat());
            params.put("maxLat", criteria.getMaxLat());
            params.put("minLon", criteria.getMinLon());
            params.put("maxLon", criteria.getMaxLon());
        }

        // 6. Age boundaries pushdown (utilizes idx_users_discovery & idx_users_active_gender_age)
        if (criteria.getMinBirthDate() != null && criteria.getMaxBirthDate() != null) {
            jpql.append("AND (u.birthDate IS NULL OR u.birthDate BETWEEN :minBirthDate AND :maxBirthDate) ");
            params.put("minBirthDate", criteria.getMinBirthDate());
            params.put("maxBirthDate", criteria.getMaxBirthDate());
        }

        // 7. Micro-circle pushdown (utilizes idx_profiles_micro_circle)
        if (criteria.getMicroCircle() != null && !criteria.getMicroCircle().isBlank()) {
            jpql.append("AND LOWER(p.microCircle) = :microCircle ");
            params.put("microCircle", criteria.getMicroCircle().trim().toLowerCase());
        }

        // 8. Dietary preferences pushdown (utilizes idx_profiles_dietary)
        if (criteria.getDietaryPreferences() != null && !criteria.getDietaryPreferences().isEmpty()) {
            jpql.append("AND (p.dietaryPref IS NULL OR p.dietaryPref IN :dietaryPrefs) ");
            params.put("dietaryPrefs", criteria.getDietaryPreferences());
        }

        // 9. Order by karma score descending to prioritize high-intent, active members (utilizes idx_users_karma)
        jpql.append("ORDER BY u.karmaScore DESC NULLS LAST");

        TypedQuery<Object[]> query = entityManager.createQuery(jpql.toString(), Object[].class);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }

        int maxResults = criteria.getLimit() > 0 ? criteria.getLimit() : 50;
        query.setMaxResults(maxResults);

        List<Object[]> rows = query.getResultList();
        List<CandidateUserRecord> results = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            User user = (User) row[0];
            Profile profile = (Profile) row[1];
            results.add(new CandidateUserRecord(user, profile));
        }

        return results;
    }
}
