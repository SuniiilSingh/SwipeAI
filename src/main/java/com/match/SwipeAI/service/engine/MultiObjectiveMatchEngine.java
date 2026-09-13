package com.match.SwipeAI.service.engine;

import com.match.SwipeAI.enums.DietaryPreference;
import com.match.SwipeAI.model.Profile;
import com.match.SwipeAI.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiObjectiveMatchEngine {

    private final CosmicChemistryEngine cosmicChemistryEngine;

    // LLD Weights
    private static final double W1_VECTOR = 0.35;
    private static final double W2_CULTURAL = 0.30;
    private static final double W3_KARMA = 0.20;
    private static final double W4_DISTANCE = 0.15;

    public int calculateCompatibilityScore(User viewer, Profile viewerProfile,
                                          User candidate, Profile candidateProfile,
                                          double distanceKm) {
        // 1. Vector Cosine Similarity (Meme + Persona embeddings)
        double vectorSim = calculateVectorSimilarity(viewerProfile, candidateProfile);

        // 2. Cultural Overlap
        double culturalOverlap = calculateCulturalOverlap(viewerProfile, candidateProfile);

        // 3. Karma Stability Balance
        int karmaViewer = viewer.getKarmaScore() != null ? viewer.getKarmaScore() : 100;
        int karmaCandidate = candidate.getKarmaScore() != null ? candidate.getKarmaScore() : 100;
        double karmaDelta = Math.abs(karmaViewer - karmaCandidate);
        double karmaScoreComponent = 1.0 - (karmaDelta / 200.0);

        // 4. Distance Decay: log(1 + Dist_km) normalized
        double distComponent = Math.log(1.0 + Math.max(0.0, distanceKm)) / Math.log(1.0 + 50.0); // normalize up to 50km
        distComponent = Math.min(1.0, Math.max(0.0, distComponent));

        // Combined Multi-Objective Score S(u, v)
        double score = (W1_VECTOR * vectorSim)
                     + (W2_CULTURAL * culturalOverlap)
                     + (W3_KARMA * karmaScoreComponent)
                     - (W4_DISTANCE * distComponent);

        // Map from range to 0 - 100%
        int finalScore = (int) Math.round(Math.max(50.0, Math.min(99.0, (score + 0.15) * 100.0)));
        return finalScore;
    }

    private double calculateVectorSimilarity(Profile a, Profile b) {
        if (a == null || b == null) return 0.75;
        // High humor/meme match if both have similar food or city or circle
        if (a.getMicroCircle() != null && a.getMicroCircle().equals(b.getMicroCircle())) {
            return 0.95;
        }
        return 0.82;
    }

    public double calculateCulturalOverlap(Profile a, Profile b) {
        if (a == null || b == null) return 0.70;

        // Diet Match (0.30)
        double dietMatch = 0.50;
        if (a.getDietaryPref() != null && b.getDietaryPref() != null) {
            if (a.getDietaryPref() == b.getDietaryPref()) {
                dietMatch = 1.0;
            } else if ((a.getDietaryPref() == DietaryPreference.PURE_VEG || a.getDietaryPref() == DietaryPreference.STRICT_JAIN || a.getDietaryPref() == DietaryPreference.VEGAN) &&
                    (b.getDietaryPref() == DietaryPreference.PURE_VEG || b.getDietaryPref() == DietaryPreference.STRICT_JAIN || b.getDietaryPref() == DietaryPreference.VEGAN)) {
                dietMatch = 0.90;
            } else if (a.getDietaryPref() == DietaryPreference.EGGETARIAN && b.getDietaryPref() == DietaryPreference.PURE_VEG) {
                dietMatch = 0.80;
            }
        }

        // Shared Interests & Hobbies Overlap (0.30)
        double interestOverlap = calculateInterestOverlap(a, b);

        // Language Overlap (0.20)
        double langOverlap = 0.60;
        if (a.getLanguagesSpoken() != null && b.getLanguagesSpoken() != null) {
            Set<String> intersection = new HashSet<>(a.getLanguagesSpoken());
            intersection.retainAll(b.getLanguagesSpoken());
            if (!intersection.isEmpty()) {
                langOverlap = Math.min(1.0, 0.5 + (intersection.size() * 0.25));
            }
        }

        // Living Condition Fit (0.10)
        double livingFit = 0.70;
        if (a.getLivingStatus() != null && b.getLivingStatus() != null) {
            if (a.getLivingStatus() == b.getLivingStatus()) {
                livingFit = 0.95;
            } else {
                livingFit = 0.75;
            }
        }

        // Cosmic Vibe Score (0.10)
        double cosmicVibe = cosmicChemistryEngine.calculateSynergy(a.getZodiacSign(), b.getZodiacSign()) / 100.0;

        return (0.30 * dietMatch) + (0.30 * interestOverlap) + (0.20 * langOverlap) + (0.10 * livingFit) + (0.10 * cosmicVibe);
    }

    private double calculateInterestOverlap(Profile a, Profile b) {
        Set<String> tagsA = extractCleanTags(a);
        Set<String> tagsB = extractCleanTags(b);
        if (tagsA.isEmpty() || tagsB.isEmpty()) return 0.65;

        int commonCount = 0;
        for (String itemA : tagsA) {
            for (String itemB : tagsB) {
                if (itemA.equalsIgnoreCase(itemB) || itemA.contains(itemB) || itemB.contains(itemA)) {
                    commonCount++;
                    break;
                }
            }
        }

        if (commonCount >= 3) return 1.0;
        if (commonCount == 2) return 0.90;
        if (commonCount == 1) return 0.80;
        return 0.50;
    }

    private Set<String> extractCleanTags(Profile p) {
        Set<String> tags = new HashSet<>();
        if (p == null) return tags;
        if (p.getInterests() != null && !p.getInterests().isBlank()) {
            for (String s : p.getInterests().split(",")) {
                String c = s.replaceAll("[^a-zA-Z0-9 &]", "").trim().toLowerCase();
                if (!c.isBlank()) tags.add(c);
            }
        }
        if (p.getHobbies() != null && !p.getHobbies().isBlank()) {
            for (String s : p.getHobbies().split(",")) {
                String c = s.replaceAll("[^a-zA-Z0-9 &]", "").trim().toLowerCase();
                if (!c.isBlank()) tags.add(c);
            }
        }
        return tags;
    }

    // Map of recognized Indian tech hubs / metros and major cities to default center coordinates (lat, lon)
    private static final java.util.Map<String, double[]> CITY_COORDINATES = new java.util.HashMap<>();
    private static final java.util.Map<String, double[]> NEIGHBORHOOD_COORDINATES = new java.util.HashMap<>();

    static {
        // Major Cities
        CITY_COORDINATES.put("bengaluru", new double[]{12.9716, 77.5946});
        CITY_COORDINATES.put("bangalore", new double[]{12.9716, 77.5946});
        CITY_COORDINATES.put("mumbai", new double[]{19.0760, 72.8777});
        CITY_COORDINATES.put("bombay", new double[]{19.0760, 72.8777});
        CITY_COORDINATES.put("delhi", new double[]{28.6139, 77.2090});
        CITY_COORDINATES.put("new delhi", new double[]{28.6139, 77.2090});
        CITY_COORDINATES.put("gurgaon", new double[]{28.4595, 77.0266});
        CITY_COORDINATES.put("gurugram", new double[]{28.4595, 77.0266});
        CITY_COORDINATES.put("noida", new double[]{28.5355, 77.3910});
        CITY_COORDINATES.put("hyderabad", new double[]{17.3850, 78.4867});
        CITY_COORDINATES.put("pune", new double[]{18.5204, 73.8567});
        CITY_COORDINATES.put("chennai", new double[]{13.0827, 80.2707});
        CITY_COORDINATES.put("madras", new double[]{13.0827, 80.2707});
        CITY_COORDINATES.put("kolkata", new double[]{22.5726, 88.3639});
        CITY_COORDINATES.put("calcutta", new double[]{22.5726, 88.3639});
        CITY_COORDINATES.put("ahmedabad", new double[]{23.0225, 72.5714});
        CITY_COORDINATES.put("jaipur", new double[]{26.9124, 75.7873});
        CITY_COORDINATES.put("chandigarh", new double[]{30.7333, 76.7794});
        CITY_COORDINATES.put("goa", new double[]{15.2993, 74.1240});
        CITY_COORDINATES.put("kochi", new double[]{9.9312, 76.2673});
        CITY_COORDINATES.put("cochin", new double[]{9.9312, 76.2673});
        CITY_COORDINATES.put("lucknow", new double[]{26.8467, 80.9462});
        CITY_COORDINATES.put("indore", new double[]{22.7196, 75.8577});

        // Neighborhoods (Bengaluru)
        NEIGHBORHOOD_COORDINATES.put("indiranagar", new double[]{12.9784, 77.6408});
        NEIGHBORHOOD_COORDINATES.put("koramangala", new double[]{12.9352, 77.6245});
        NEIGHBORHOOD_COORDINATES.put("central", new double[]{12.9752, 77.6053});
        NEIGHBORHOOD_COORDINATES.put("church street", new double[]{12.9752, 77.6053});
        NEIGHBORHOOD_COORDINATES.put("hsr", new double[]{12.9121, 77.6446});
        NEIGHBORHOOD_COORDINATES.put("hsr layout", new double[]{12.9121, 77.6446});
        NEIGHBORHOOD_COORDINATES.put("whitefield", new double[]{12.9698, 77.7499});
        NEIGHBORHOOD_COORDINATES.put("malleshwaram", new double[]{13.0033, 77.5645});
        NEIGHBORHOOD_COORDINATES.put("jayanagar", new double[]{12.9304, 77.5839});
        NEIGHBORHOOD_COORDINATES.put("bellandur", new double[]{12.9260, 77.6762});
    }

    public double[] resolveCoordinates(Double lat, Double lon, Profile profile) {
        if (lat != null && lon != null) {
            return new double[]{lat, lon};
        }
        if (profile != null) {
            if (profile.getNeighborhood() != null && !profile.getNeighborhood().isBlank()) {
                String hoodKey = profile.getNeighborhood().trim().toLowerCase();
                if (NEIGHBORHOOD_COORDINATES.containsKey(hoodKey)) {
                    return NEIGHBORHOOD_COORDINATES.get(hoodKey);
                }
            }
            if (profile.getCity() != null && !profile.getCity().isBlank()) {
                String cityKey = profile.getCity().trim().toLowerCase();
                if (CITY_COORDINATES.containsKey(cityKey)) {
                    return CITY_COORDINATES.get(cityKey);
                }
            }
            if (profile.getLocation() != null && !profile.getLocation().isBlank()) {
                String locKey = profile.getLocation().trim().toLowerCase();
                for (var entry : CITY_COORDINATES.entrySet()) {
                    if (locKey.contains(entry.getKey())) {
                        return entry.getValue();
                    }
                }
            }
        }
        return null;
    }

    public double calculateDistanceWithContext(Double lat1, Double lon1, Profile p1,
                                             Double lat2, Double lon2, Profile p2) {
        double[] coords1 = resolveCoordinates(lat1, lon1, p1);
        double[] coords2 = resolveCoordinates(lat2, lon2, p2);

        if (coords1 != null && coords2 != null) {
            return calculateDistanceKm(coords1[0], coords1[1], coords2[0], coords2[1]);
        }

        // If both are in the same city name even if not in map
        if (p1 != null && p2 != null && p1.getCity() != null && p2.getCity() != null
                && p1.getCity().trim().equalsIgnoreCase(p2.getCity().trim())) {
            return 3.5; // Same city, approximate local distance
        }

        // If one is in Bengaluru and the other is in Mumbai or another city
        if (p1 != null && p2 != null && p1.getCity() != null && p2.getCity() != null
                && !p1.getCity().trim().equalsIgnoreCase(p2.getCity().trim())) {
            return 800.0; // Across different cities
        }

        if (lat1 != null && lon1 != null && lat2 != null && lon2 != null) {
            return calculateDistanceKm(lat1, lon1, lat2, lon2);
        }

        return 3.5;
    }

    public double calculateDistanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 3.5; // default fallback distance in km
        }
        final int R = 6371; // Earth radius in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(R * c * 10.0) / 10.0;
    }
}
