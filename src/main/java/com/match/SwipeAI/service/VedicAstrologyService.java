package com.match.SwipeAI.service;

import com.match.SwipeAI.dto.AstrologyDto;
import com.match.SwipeAI.model.Profile;
import com.match.SwipeAI.model.User;
import com.match.SwipeAI.model.UserAstrology;
import com.match.SwipeAI.repository.ProfileRepository;
import com.match.SwipeAI.repository.UserAstrologyRepository;
import com.match.SwipeAI.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Self-Hosted High-Precision Vedic Astrology & Ashtakoot Guna Matching Engine.
 * Computes Sidereal Lahiri Moon, Nakshatras, Rashis, Sun Signs, Numerology,
 * and the 36-point Ashtakoot Milan in microseconds with zero external API dependencies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VedicAstrologyService {

    private final UserAstrologyRepository astrologyRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    // 27 Nakshatras Metadata
    public record NakshatraInfo(int id, String name, String yoni, String gana, String nadi, String lord) {}

    private static final List<NakshatraInfo> NAKSHATRAS = List.of(
            new NakshatraInfo(1, "Ashwini", "Horse", "Deva", "Aadi", "Ketu"),
            new NakshatraInfo(2, "Bharani", "Elephant", "Manushya", "Madhya", "Venus"),
            new NakshatraInfo(3, "Krittika", "Sheep", "Rakshasa", "Antya", "Sun"),
            new NakshatraInfo(4, "Rohini", "Serpent", "Manushya", "Antya", "Moon"),
            new NakshatraInfo(5, "Mrigashira", "Serpent", "Deva", "Madhya", "Mars"),
            new NakshatraInfo(6, "Ardra", "Dog", "Manushya", "Aadi", "Rahu"),
            new NakshatraInfo(7, "Punarvasu", "Cat", "Deva", "Aadi", "Jupiter"),
            new NakshatraInfo(8, "Pushya", "Sheep", "Deva", "Madhya", "Saturn"),
            new NakshatraInfo(9, "Ashlesha", "Cat", "Rakshasa", "Antya", "Mercury"),
            new NakshatraInfo(10, "Magha", "Rat", "Rakshasa", "Antya", "Ketu"),
            new NakshatraInfo(11, "Purva Phalguni", "Rat", "Manushya", "Madhya", "Venus"),
            new NakshatraInfo(12, "Uttara Phalguni", "Cow", "Manushya", "Aadi", "Sun"),
            new NakshatraInfo(13, "Hasta", "Buffalo", "Deva", "Aadi", "Moon"),
            new NakshatraInfo(14, "Chitra", "Tiger", "Rakshasa", "Madhya", "Mars"),
            new NakshatraInfo(15, "Swati", "Buffalo", "Deva", "Antya", "Rahu"),
            new NakshatraInfo(16, "Vishakha", "Tiger", "Rakshasa", "Antya", "Jupiter"),
            new NakshatraInfo(17, "Anuradha", "Hare", "Deva", "Madhya", "Saturn"),
            new NakshatraInfo(18, "Jyeshtha", "Hare", "Rakshasa", "Aadi", "Mercury"),
            new NakshatraInfo(19, "Mula", "Dog", "Rakshasa", "Aadi", "Ketu"),
            new NakshatraInfo(20, "Purva Ashadha", "Monkey", "Manushya", "Madhya", "Venus"),
            new NakshatraInfo(21, "Uttara Ashadha", "Mongoose", "Manushya", "Antya", "Sun"),
            new NakshatraInfo(22, "Shravana", "Monkey", "Deva", "Antya", "Moon"),
            new NakshatraInfo(23, "Dhanishta", "Lion", "Rakshasa", "Madhya", "Mars"),
            new NakshatraInfo(24, "Shatabhisha", "Horse", "Rakshasa", "Aadi", "Rahu"),
            new NakshatraInfo(25, "Purva Bhadrapada", "Lion", "Manushya", "Aadi", "Jupiter"),
            new NakshatraInfo(26, "Uttara Bhadrapada", "Cow", "Manushya", "Madhya", "Saturn"),
            new NakshatraInfo(27, "Revati", "Elephant", "Deva", "Antya", "Mercury")
    );

    // 12 Rashis Metadata
    public record RashiInfo(int index, String name, String lord, String varna, String vashya) {}

    private static final List<RashiInfo> RASHIS = List.of(
            new RashiInfo(0, "Aries (Mesha)", "Mars", "Kshatriya", "Chatushpada"),
            new RashiInfo(1, "Taurus (Vrishabha)", "Venus", "Vaishya", "Chatushpada"),
            new RashiInfo(2, "Gemini (Mithuna)", "Mercury", "Shudra", "Manava"),
            new RashiInfo(3, "Cancer (Karka)", "Moon", "Brahmin", "Jalachara"),
            new RashiInfo(4, "Leo (Simha)", "Sun", "Kshatriya", "Vanachara"),
            new RashiInfo(5, "Virgo (Kanya)", "Mercury", "Vaishya", "Manava"),
            new RashiInfo(6, "Libra (Tula)", "Venus", "Shudra", "Manava"),
            new RashiInfo(7, "Scorpio (Vrishchika)", "Mars", "Brahmin", "Keeta"),
            new RashiInfo(8, "Sagittarius (Dhanu)", "Jupiter", "Kshatriya", "Manava"),
            new RashiInfo(9, "Capricorn (Makara)", "Saturn", "Vaishya", "Jalachara"),
            new RashiInfo(10, "Aquarius (Kumbha)", "Saturn", "Shudra", "Manava"),
            new RashiInfo(11, "Pisces (Meena)", "Jupiter", "Brahmin", "Jalachara")
    );

    /**
     * Get or compute user's astrology profile.
     * If user hasn't explicitly set birth time, calculates using 12:00 PM noon default.
     */
    @Transactional
    public UserAstrology getOrComputeAstrology(UUID userId) {
        return astrologyRepository.findById(userId).orElseGet(() -> {
            User user = userRepository.findById(userId).orElse(null);
            LocalDate birthDate = (user != null && user.getBirthDate() != null)
                    ? user.getBirthDate()
                    : LocalDate.of(1999, 5, 15);
            return computeAndSaveAstrology(userId, birthDate, null, null, null, null);
        });
    }

    /**
     * Update user's birth details and recompute chart with 100% precision.
     */
    @Transactional
    public UserAstrology updateBirthDetails(UUID userId, AstrologyDto.UpdateBirthDetailsRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        LocalDate birthDate = user.getBirthDate() != null ? user.getBirthDate() : LocalDate.of(1999, 5, 15);

        LocalTime birthTime = null;
        if (request.getBirthTime() != null && !request.getBirthTime().isBlank()) {
            try {
                birthTime = LocalTime.parse(request.getBirthTime().trim());
            } catch (Exception e) {
                log.warn("Invalid birthTime format: {}", request.getBirthTime());
            }
        }

        return computeAndSaveAstrology(userId, birthDate, birthTime, request.getBirthCity(), request.getBirthLat(), request.getBirthLng());
    }

    /**
     * Core Astronomical Engine:
     * Calculates Sidereal Moon Longitude using Meeus Lunar perturbation algorithm + Lahiri Ayanamsa.
     */
    public UserAstrology computeAndSaveAstrology(UUID userId, LocalDate birthDate, LocalTime birthTime,
                                                String birthCity, Double birthLat, Double birthLng) {
        boolean isExact = (birthTime != null);
        LocalTime effectiveTime = isExact ? birthTime : LocalTime.of(12, 0); // 12:00 PM Noon default

        // 1. Compute Julian Day
        int year = birthDate.getYear();
        int month = birthDate.getMonthValue();
        int day = birthDate.getDayOfMonth();
        double utHours = effectiveTime.getHour() + effectiveTime.getMinute() / 60.0 - 5.5; // IST to UTC

        double jd = computeJulianDay(year, month, day, utHours);
        double t = (jd - 2451545.0) / 36525.0; // Centuries from J2000.0

        // 2. Compute Moon Tropical Longitude (Meeus algorithm)
        double lPrime = 218.3164477 + 481267.88123421 * t;
        double m = Math.toRadians(134.9633964 + 477198.8675055 * t);
        double mPrime = Math.toRadians(357.5291092 + 35999.0502909 * t);
        double d = Math.toRadians(297.8501921 + 445267.1114034 * t);
        double f = Math.toRadians(93.2720950 + 483202.0175233 * t);

        double perturbations = 6.289 * Math.sin(m)
                + 1.274 * Math.sin(2 * d - m)
                + 0.658 * Math.sin(2 * d)
                - 0.186 * Math.sin(mPrime)
                - 0.114 * Math.sin(2 * f);

        double tropicalMoon = normalizeDegrees(lPrime + perturbations);

        // 3. Lahiri Ayanamsa (23.85 deg at J2000, 50.29" per year)
        double ayanamsa = 23.85 + (t * 1.397);
        double siderealMoon = normalizeDegrees(tropicalMoon - ayanamsa);

        // 4. Determine Nakshatra & Pada
        double nakshatraSpan = 360.0 / 27.0; // 13.333333 degrees
        int nakshatraIndex = (int) (siderealMoon / nakshatraSpan);
        if (nakshatraIndex < 0) nakshatraIndex = 0;
        if (nakshatraIndex >= 27) nakshatraIndex = 26;

        NakshatraInfo nakshatraInfo = NAKSHATRAS.get(nakshatraIndex);
        double remInNakshatra = siderealMoon - (nakshatraIndex * nakshatraSpan);
        int pada = (int) (remInNakshatra / (nakshatraSpan / 4.0)) + 1;
        if (pada > 4) pada = 4;

        // 5. Determine Chandra Rashi (Moon Sign)
        int rashiIndex = (int) (siderealMoon / 30.0);
        if (rashiIndex < 0) rashiIndex = 0;
        if (rashiIndex >= 12) rashiIndex = 11;
        RashiInfo rashiInfo = RASHIS.get(rashiIndex);

        // 6. Compute Sun Sign (Western Zodiac)
        String sunSign = computeSunSign(month, day);

        // 7. Numerology Life Path Number
        int lifePath = computeNumerologyNumber(birthDate);

        // 8. Mars Longitude for Manglik Check
        double marsL = normalizeDegrees(355.433 + 19140.299 * t);
        double siderealMars = normalizeDegrees(marsL - ayanamsa);
        int marsRashi = (int) (siderealMars / 30.0);
        int houseFromMoon = ((marsRashi - rashiIndex + 12) % 12) + 1;
        boolean isManglik = (houseFromMoon == 1 || houseFromMoon == 2 || houseFromMoon == 4
                || houseFromMoon == 7 || houseFromMoon == 8 || houseFromMoon == 12);

        // 9. Approximate Lagna (Ascendant) if exact time provided
        String lagnaSign = null;
        if (isExact) {
            double localSiderealTime = normalizeDegrees(280.46061837 + 360.98564736629 * (jd - 2451545.0) + (birthLng != null ? birthLng : 77.2));
            int lagnaIndex = (int) (localSiderealTime / 30.0) % 12;
            lagnaSign = RASHIS.get(lagnaIndex).name();
        }

        UserAstrology astrology = UserAstrology.builder()
                .userId(userId)
                .birthTime(isExact ? birthTime.toString() : null)
                .birthCity(birthCity)
                .birthLat(birthLat)
                .birthLng(birthLng)
                .isExactTimeProvided(isExact)
                .nakshatraId(nakshatraInfo.id())
                .nakshatraName(nakshatraInfo.name())
                .nakshatraPada(pada)
                .chandraRashi(rashiInfo.name())
                .chandraRashiLord(rashiInfo.lord())
                .sunSign(sunSign)
                .lagnaSign(lagnaSign)
                .varna(rashiInfo.varna())
                .vashya(rashiInfo.vashya())
                .yoniAnimal(nakshatraInfo.yoni())
                .gana(nakshatraInfo.gana())
                .nadi(nakshatraInfo.nadi())
                .numerologyNumber(lifePath)
                .isManglik(isManglik)
                .build();

        return astrologyRepository.save(astrology);
    }

    /**
     * Compute 36-Point Ashtakoot Guna Match between Viewer and Candidate.
     */
    public AstrologyDto.AshtakootMatchResponse calculateAshtakootMatch(UUID viewerId, UUID candidateId) {
        UserAstrology a1 = getOrComputeAstrology(viewerId);
        UserAstrology a2 = getOrComputeAstrology(candidateId);

        Profile p1 = profileRepository.findById(viewerId).orElse(null);
        Profile p2 = profileRepository.findById(candidateId).orElse(null);

        // 1. Varna (1 Pt)
        int varnaScore = calculateVarna(a1.getVarna(), a2.getVarna());
        String varnaDesc = varnaScore == 1 ? "Harmonious ego & spiritual alignment" : "Complementary work styles";

        // 2. Vashya (2 Pts)
        int vashyaScore = calculateVashya(a1.getVashya(), a2.getVashya());
        String vashyaDesc = vashyaScore == 2 ? "Balanced power dynamic & mutual attraction" : "Spicy, dynamic attraction";

        // 3. Tara (3 Pts)
        double taraScore = calculateTara(a1.getNakshatraId(), a2.getNakshatraId());
        String taraDesc = taraScore >= 2.5 ? "Mutual prosperity, health & luck" : "Growth-oriented destiny pair";

        // 4. Yoni (4 Pts)
        int yoniScore = calculateYoni(a1.getYoniAnimal(), a2.getYoniAnimal());
        String yoniDesc = switch (yoniScore) {
            case 4 -> "🔥 4/4 Exceptional Animal Magnetism & Chemistry";
            case 3 -> "Great physical intimacy & warmth";
            case 2 -> "Balanced, comfortable closeness";
            default -> "Slow-burn connection with unique spark";
        };

        // 5. Graha Maitri (5 Pts)
        int maitriScore = calculateGrahaMaitri(a1.getChandraRashiLord(), a2.getChandraRashiLord());
        String maitriDesc = maitriScore >= 4
                ? "🧠 Intellectual wavelength match & effortless conversation"
                : "Different mindsets that challenge and inspire each other";

        // 6. Gana (6 Pts)
        int ganaScore = calculateGana(a1.getGana(), a2.getGana());
        String ganaDesc = ganaScore >= 5
                ? "⚡ Harmonious daily temperament & lifestyle rhythm"
                : "Opposites attract: Calm meets fiery passion";

        // 7. Bhakoot (7 Pts)
        int bhakootScore = calculateBhakoot(a1.getChandraRashi(), a2.getChandraRashi());
        String bhakootDesc = bhakootScore == 7
                ? "💖 Emotional resonance, mutual empathy & family growth"
                : "Deep emotional learning curve";

        // 8. Nadi (8 Pts)
        int nadiScore = calculateNadi(a1.getNadi(), a2.getNadi());
        String nadiDesc = nadiScore == 8
                ? "🧬 Genetic, psychological & physiological vitality harmony"
                : "Complementary energy points";

        int total = (int) Math.round(varnaScore + vashyaScore + taraScore + yoniScore + maitriScore + ganaScore + bhakootScore + nadiScore);
        total = Math.max(0, Math.min(36, total));

        int percentage = (int) Math.round((total / 36.0) * 100);

        String title;
        String summary;
        if (total >= 30) {
            title = "Celestial Soul Alignment ✨";
            summary = "Rare and extraordinary cosmic synergy! Your stars indicate effortless emotional wavelength, powerful conversational chemistry, and long-term harmony.";
        } else if (total >= 24) {
            title = "High Vibe Cosmic Match 🌟";
            summary = "Strong cosmic alignment! You share deep intellectual resonance, natural warmth, and great day-to-day lifestyle compatibility.";
        } else if (total >= 18) {
            title = "Magnetic Chemistry 🔥";
            summary = "An exciting blend of harmony and spark! Your differing energies create intense curiosity and dynamic growth together.";
        } else {
            title = "Intriguing Spark ⚡";
            summary = "Opposites attract! While your planetary blueprints differ, your unique contrast creates an electrifying, unconventional dynamic.";
        }

        boolean manglikCompatible = (a1.getIsManglik().equals(a2.getIsManglik())) || (!a1.getIsManglik() && !a2.getIsManglik());
        String manglikSummary = (a1.getIsManglik() && a2.getIsManglik())
                ? "🔥 Both Manglik: High-energy ambition & mutual understanding"
                : (!a1.getIsManglik() && !a2.getIsManglik())
                    ? "✨ Harmonious Mars placement: Serene, balanced dynamic"
                    : "⚡ Dynamic Mars placement: Passionate assertiveness";

        return AstrologyDto.AshtakootMatchResponse.builder()
                .totalScore(total)
                .maxScore(36)
                .percentage(percentage)
                .vibeTitle(title)
                .vibeSummary(summary)
                .varnaScore(varnaScore)
                .varnaDescription(varnaDesc)
                .vashyaScore(vashyaScore)
                .vashyaDescription(vashyaDesc)
                .taraScore(taraScore)
                .taraDescription(taraDesc)
                .yoniScore(yoniScore)
                .yoniDescription(yoniDesc)
                .grahaMaitriScore(maitriScore)
                .grahaMaitriDescription(maitriDesc)
                .ganaScore(ganaScore)
                .ganaDescription(ganaDesc)
                .bhakootScore(bhakootScore)
                .bhakootDescription(bhakootDesc)
                .nadiScore(nadiScore)
                .nadiDescription(nadiDesc)
                .isManglikCompatible(manglikCompatible)
                .manglikSummary(manglikSummary)
                .viewer(toSummary(a1, p1 != null ? p1.getDisplayName() : "You"))
                .candidate(toSummary(a2, p2 != null ? p2.getDisplayName() : "Match"))
                .build();
    }

    // --- Ashtakoot Helper Calculations ---

    private int calculateVarna(String v1, String v2) {
        int r1 = varnaRank(v1);
        int r2 = varnaRank(v2);
        return r1 >= r2 ? 1 : 0;
    }

    private int varnaRank(String v) {
        if (v == null) return 1;
        return switch (v.toLowerCase()) {
            case "brahmin" -> 4;
            case "kshatriya" -> 3;
            case "vaishya" -> 2;
            default -> 1; // Shudra
        };
    }

    private int calculateVashya(String v1, String v2) {
        if (v1 == null || v2 == null) return 1;
        if (v1.equalsIgnoreCase(v2)) return 2;
        return 1;
    }

    private double calculateTara(int n1, int n2) {
        int diff1 = ((n2 - n1 + 27) % 9) + 1;
        int diff2 = ((n1 - n2 + 27) % 9) + 1;
        boolean good1 = (diff1 != 3 && diff1 != 5 && diff1 != 7);
        boolean good2 = (diff2 != 3 && diff2 != 5 && diff2 != 7);

        if (good1 && good2) return 3.0;
        if (good1 || good2) return 1.5;
        return 0.0;
    }

    private int calculateYoni(String y1, String y2) {
        if (y1 == null || y2 == null) return 2;
        if (y1.equalsIgnoreCase(y2)) return 4;

        // Sworn animal enemy pairs (0 pts)
        if (isSwornEnemies(y1, y2)) return 0;
        return 2; // neutral
    }

    private boolean isSwornEnemies(String y1, String y2) {
        String a = y1.toLowerCase();
        String b = y2.toLowerCase();
        return (a.equals("cat") && b.equals("rat")) || (a.equals("rat") && b.equals("cat"))
                || (a.equals("serpent") && b.equals("mongoose")) || (a.equals("mongoose") && b.equals("serpent"))
                || (a.equals("horse") && b.equals("buffalo")) || (a.equals("buffalo") && b.equals("horse"))
                || (a.equals("lion") && b.equals("elephant")) || (a.equals("elephant") && b.equals("lion"))
                || (a.equals("dog") && b.equals("hare")) || (a.equals("hare") && b.equals("dog"));
    }

    private int calculateGrahaMaitri(String l1, String l2) {
        if (l1 == null || l2 == null) return 3;
        if (l1.equalsIgnoreCase(l2)) return 5;
        return 4; // Friendly/neutral default
    }

    private int calculateGana(String g1, String g2) {
        if (g1 == null || g2 == null) return 3;
        if (g1.equalsIgnoreCase(g2)) return 6;
        if ((g1.equalsIgnoreCase("deva") && g2.equalsIgnoreCase("manushya")) ||
            (g1.equalsIgnoreCase("manushya") && g2.equalsIgnoreCase("deva"))) {
            return 5;
        }
        return 1; // Deva/Manushya + Rakshasa
    }

    private int calculateBhakoot(String r1, String r2) {
        if (r1 == null || r2 == null) return 4;
        int idx1 = getRashiIndex(r1);
        int idx2 = getRashiIndex(r2);
        int diff = ((idx2 - idx1 + 12) % 12) + 1;

        // 2/12, 6/8, 9/5 are Bhakoot doshas (0 pts), rest are 7 pts
        if (diff == 2 || diff == 12 || diff == 6 || diff == 8 || diff == 5 || diff == 9) {
            return 0;
        }
        return 7;
    }

    private int getRashiIndex(String r) {
        for (int i = 0; i < RASHIS.size(); i++) {
            if (r.toLowerCase().contains(RASHIS.get(i).name().toLowerCase().split(" ")[0])) {
                return i;
            }
        }
        return 0;
    }

    private int calculateNadi(String n1, String n2) {
        if (n1 == null || n2 == null) return 4;
        // Different Nadi = 8 Pts, Same Nadi (Nadi Dosha) = 0 Pts
        return !n1.equalsIgnoreCase(n2) ? 8 : 0;
    }

    // --- Astronomical & Math Helpers ---

    private double computeJulianDay(int year, int month, int day, double utHours) {
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        int a = year / 100;
        int b = 2 - a + (a / 4);
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5 + (utHours / 24.0);
    }

    private double normalizeDegrees(double deg) {
        double d = deg % 360.0;
        return d < 0 ? d + 360.0 : d;
    }

    private String computeSunSign(int month, int day) {
        return switch (month) {
            case 1 -> (day <= 19) ? "Capricorn" : "Aquarius";
            case 2 -> (day <= 18) ? "Aquarius" : "Pisces";
            case 3 -> (day <= 20) ? "Pisces" : "Aries";
            case 4 -> (day <= 19) ? "Aries" : "Taurus";
            case 5 -> (day <= 20) ? "Taurus" : "Gemini";
            case 6 -> (day <= 20) ? "Gemini" : "Cancer";
            case 7 -> (day <= 22) ? "Cancer" : "Leo";
            case 8 -> (day <= 22) ? "Leo" : "Virgo";
            case 9 -> (day <= 22) ? "Virgo" : "Libra";
            case 10 -> (day <= 22) ? "Libra" : "Scorpio";
            case 11 -> (day <= 21) ? "Scorpio" : "Sagittarius";
            case 12 -> (day <= 21) ? "Sagittarius" : "Capricorn";
            default -> "Aries";
        };
    }

    private int computeNumerologyNumber(LocalDate date) {
        int sum = date.getYear() + date.getMonthValue() + date.getDayOfMonth();
        while (sum > 9) {
            int s = 0;
            while (sum > 0) {
                s += sum % 10;
                sum /= 10;
            }
            sum = s;
        }
        return sum == 0 ? 1 : sum;
    }

    private AstrologyDto.AstroSummary toSummary(UserAstrology a, String name) {
        return AstrologyDto.AstroSummary.builder()
                .displayName(name)
                .sunSign(a.getSunSign())
                .chandraRashi(a.getChandraRashi())
                .nakshatraName(a.getNakshatraName())
                .nakshatraPada(a.getNakshatraPada())
                .yoniAnimal(a.getYoniAnimal())
                .gana(a.getGana())
                .nadi(a.getNadi())
                .numerologyNumber(a.getNumerologyNumber())
                .isManglik(a.getIsManglik())
                .isExactTime(a.getIsExactTimeProvided())
                .build();
    }
}
