package com.match.SwipeAI.service.engine;

import com.match.SwipeAI.dto.ProfileDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CosmicChemistryEngine {

    private static final Map<String, String> ELEMENT_MAP = Map.ofEntries(
            Map.entry("Aries", "Fire"), Map.entry("Leo", "Fire"), Map.entry("Sagittarius", "Fire"),
            Map.entry("Taurus", "Earth"), Map.entry("Virgo", "Earth"), Map.entry("Capricorn", "Earth"),
            Map.entry("Gemini", "Air"), Map.entry("Libra", "Air"), Map.entry("Aquarius", "Air"),
            Map.entry("Cancer", "Water"), Map.entry("Scorpio", "Water"), Map.entry("Pisces", "Water")
    );

    public int calculateSynergy(String signA, String signB) {
        if (signA == null || signB == null) return 85;

        String elA = ELEMENT_MAP.getOrDefault(signA, "Fire");
        String elB = ELEMENT_MAP.getOrDefault(signB, "Fire");

        if (elA.equals(elB)) return 94; // Same element = extreme harmony
        if ((elA.equals("Fire") && elB.equals("Air")) || (elA.equals("Air") && elB.equals("Fire"))) return 91; // Fire + Air
        if ((elA.equals("Earth") && elB.equals("Water")) || (elA.equals("Water") && elB.equals("Earth"))) return 88; // Earth + Water
        return 78;
    }

    public ProfileDto.CosmicChemistryResponse generateVibeCard(String viewerSign, String candidateSign,
                                                               String candidateName) {
        if (viewerSign == null) viewerSign = "Taurus";
        if (candidateSign == null) candidateSign = "Leo";

        int synergy = calculateSynergy(viewerSign, candidateSign);
        String headline = String.format("Your Sun in %s + %s's Moon in %s = %d%% Cosmic Vibe Synergy",
                viewerSign, candidateName != null ? candidateName : "Match", candidateSign, synergy);

        String vibeReport = String.format(
                "Dynamic blend of grounded intention and spontaneous warmth. You provide the calm anchor, while %s brings playful weekend energy.",
                candidateName != null ? candidateName : "they");

        List<String> strengths = List.of(
                "Shared taste in quiet aesthetic cafes & late-night drives",
                "High mutual respect for personal space & career ambitions",
                "Effortless banter rhythm — 0 awkward pauses"
        );

        String spark = String.format("Ask %s if they believe in mercury retrograde or if they just use it as an excuse for bad texting habits!",
                candidateName != null ? candidateName : "them");

        return ProfileDto.CosmicChemistryResponse.builder()
                .viewerSign(viewerSign)
                .candidateSign(candidateSign)
                .overallSynergyScore(synergy)
                .headline(headline)
                .vibeReport(vibeReport)
                .sharedStrengths(strengths)
                .conversationalSpark(spark)
                .build();
    }
}
