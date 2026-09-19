package com.match.SwipeAI.service.engine;

import com.match.SwipeAI.dto.MatchDto;
import com.match.SwipeAI.enums.DietaryPreference;
import com.match.SwipeAI.enums.LivingStatus;
import com.match.SwipeAI.model.Profile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Alternative 1: Algorithmic Mutual Chemistry Sparks Engine.
 * Replaces external AI Wingman with pure data-driven profile comparison.
 * Zero external LLM cost, sub-1ms response time, 100% authentic user data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MutualChemistrySparksEngine {

    /**
     * Calculates 3 contextual, authentic conversation sparks between two matched profiles.
     *
     * @param viewerProfile Profile of the user initiating the view
     * @param candidateProfile Profile of the matched candidate
     * @param quiz Current state of the 10-second rapid icebreaker quiz (if available)
     * @return List of 3 tailored conversation starters
     */
    public List<String> generateMutualSparks(Profile viewerProfile, Profile candidateProfile, MatchDto.IcebreakerQuizDto quiz) {
        List<String> sparks = new ArrayList<>();
        String candidateName = (candidateProfile != null && candidateProfile.getDisplayName() != null && !candidateProfile.getDisplayName().isBlank())
                ? candidateProfile.getDisplayName()
                : "Match";

        if (candidateProfile == null) {
            return List.of(
                    "Hey " + candidateName + "! What's the soundtrack to your current week?",
                    "Filter coffee or cutting chai on a lazy evening?",
                    "What's one thing that always makes you smile?"
            );
        }

        // 1. Icebreaker Quiz Agreement (Highest priority when available)
        if (quiz != null && quiz.isCompleted()) {
            String quizSpark = generateQuizSpark(quiz, candidateName);
            if (quizSpark != null) {
                sparks.add(quizSpark);
            }
        }

        // 2. Shared Interests & Hobbies Overlap
        if (viewerProfile != null) {
            List<String> sharedInterests = findCommonInterests(viewerProfile, candidateProfile);
            if (!sharedInterests.isEmpty()) {
                String topInterest = sharedInterests.get(0);
                sparks.add(createInterestSpark(topInterest, candidateName));
                if (sharedInterests.size() > 1 && sparks.size() < 3) {
                    sparks.add(createInterestSpark(sharedInterests.get(1), candidateName));
                }
            }
        }

        // 3. Dietary & Lifestyle Compatibility
        if (viewerProfile != null && sparks.size() < 3) {
            String lifestyleSpark = generateLifestyleSpark(viewerProfile, candidateProfile, candidateName);
            if (lifestyleSpark != null) {
                sparks.add(lifestyleSpark);
            }
        }

        // 4. Candidate Prompt Answer Highlight
        if (sparks.size() < 3 && candidateProfile.getProfilePromptAnswer() != null && !candidateProfile.getProfilePromptAnswer().isBlank()) {
            String q = candidateProfile.getProfilePromptQuestion() != null ? candidateProfile.getProfilePromptQuestion() : "Your prompt";
            String ans = candidateProfile.getProfilePromptAnswer().trim();
            if (ans.length() > 60) ans = ans.substring(0, 57) + "...";
            sparks.add(String.format("Loved your answer to \"%s\": '%s' — let's debate that!", q, ans));
        }

        // 5. Career & Location Context
        if (sparks.size() < 3 && candidateProfile.getOccupation() != null && !candidateProfile.getOccupation().isBlank()) {
            String companyStr = (candidateProfile.getCompany() != null && !candidateProfile.getCompany().isBlank())
                    ? " @ " + candidateProfile.getCompany()
                    : "";
            sparks.add(String.format("Working as %s%s sounds exciting! What's the coolest project you're tackling right now?",
                    candidateProfile.getOccupation(), companyStr));
        }

        // 6. City / Neighborhood Spark
        if (sparks.size() < 3) {
            String city = candidateProfile.getCity() != null && !candidateProfile.getCity().isBlank()
                    ? candidateProfile.getCity()
                    : (candidateProfile.getLocation() != null && !candidateProfile.getLocation().isBlank() ? candidateProfile.getLocation() : "Bangalore");
            sparks.add(String.format("If we were grabbing chai or coffee in %s right now, what's your go-to spot?", city));
        }

        // 7. General High-Vibe Fallbacks
        if (sparks.size() < 3) {
            sparks.add(String.format("10/10 energy on your profile, %s! What made you smile today?", candidateName));
        }
        if (sparks.size() < 3) {
            sparks.add("Rameshwaram Cafe vs CTR — which side of the Indiranagar butter dosa debate are you on?");
        }

        // Return exactly top 3 unique sparks
        return sparks.stream().distinct().limit(3).collect(Collectors.toList());
    }

    private String generateQuizSpark(MatchDto.IcebreakerQuizDto quiz, String candidateName) {
        if (quiz.isMutualAgreement()) {
            Integer ans = quiz.getUserAAnswer() != null ? quiz.getUserAAnswer() : quiz.getUserBAnswer();
            if (ans != null && quiz.getOptions() != null && ans >= 0 && ans < quiz.getOptions().size()) {
                String optionText = quiz.getOptions().get(ans);
                if (optionText.toLowerCase().contains("coffee") || optionText.toLowerCase().contains("dosa")) {
                    return "⚡ 100% agreement on the Sunday Dosa crawl! Rameshwaram or CTR butter masala dosa?";
                } else if (optionText.toLowerCase().contains("sleep") || optionText.toLowerCase().contains("podcast")) {
                    return "⚡ Both voted to sleep in & binge podcasts! What is your current show obsession?";
                } else if (optionText.toLowerCase().contains("road trip") || optionText.toLowerCase().contains("nandi")) {
                    return "⚡ Mutual road trip vibe! Who's taking the wheel and who's controlling aux?";
                }
                return String.format("⚡ 100%% agreement on '%s'! We're definitely on the exact same wavelength.", optionText);
            }
            return "⚡ Both of us picked the exact same answer! What are the odds?";
        } else if (quiz.getUserAAnswer() != null && quiz.getUserBAnswer() != null && quiz.getOptions() != null) {
            int a = quiz.getUserAAnswer();
            int b = quiz.getUserBAnswer();
            if (a >= 0 && a < quiz.getOptions().size() && b >= 0 && b < quiz.getOptions().size()) {
                return String.format("⚡ Sunday debate time! You voted for '%s' — think you can convince me?", quiz.getOptions().get(b));
            }
        }
        return null;
    }

    public List<String> findCommonInterests(Profile a, Profile b) {
        Set<String> setA = extractNormalizedTags(a);
        Set<String> setB = extractNormalizedTags(b);

        List<String> common = new ArrayList<>();
        for (String tagA : setA) {
            for (String tagB : setB) {
                if (tagA.equalsIgnoreCase(tagB) || tagA.contains(tagB) || tagB.contains(tagA)) {
                    common.add(tagA);
                    break;
                }
            }
        }
        return common;
    }

    private Set<String> extractNormalizedTags(Profile p) {
        Set<String> tags = new LinkedHashSet<>();
        if (p == null) return tags;

        if (p.getInterests() != null && !p.getInterests().isBlank()) {
            for (String item : p.getInterests().split(",")) {
                String cleaned = cleanTag(item);
                if (!cleaned.isBlank()) tags.add(cleaned);
            }
        }
        if (p.getHobbies() != null && !p.getHobbies().isBlank()) {
            for (String item : p.getHobbies().split(",")) {
                String cleaned = cleanTag(item);
                if (!cleaned.isBlank()) tags.add(cleaned);
            }
        }
        return tags;
    }

    private String cleanTag(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^a-zA-Z0-9 &]", "").trim();
    }

    private String createInterestSpark(String interest, String candidateName) {
        String lower = interest.toLowerCase();
        if (lower.contains("coffee") || lower.contains("cafe")) {
            return "☕ You both love specialty coffee! What's your go-to roastery or cafe in town?";
        }
        if (lower.contains("cycling") || lower.contains("cycle") || lower.contains("bike")) {
            return "🚴 Fellow cyclists! Have you done an early morning weekend ride to Cubbon or Nandi?";
        }
        if (lower.contains("trek") || lower.contains("hiking") || lower.contains("hike")) {
            return "🥾 Both into trekking & outdoors! What's the best trail or summit you've explored?";
        }
        if (lower.contains("read") || lower.contains("book")) {
            return "📚 Fellow book lovers! What's one book you find yourself recommending to everyone?";
        }
        if (lower.contains("stand") || lower.contains("comedy")) {
            return "🎙️ Both into stand-up comedy! Who's your favorite comedian you've watched live?";
        }
        if (lower.contains("gym") || lower.contains("fitness") || lower.contains("workout")) {
            return "🏋️ Both prioritize fitness! Morning workout warrior or evening gym crew?";
        }
        if (lower.contains("yoga") || lower.contains("meditation")) {
            return "🧘 Shared mindfulness vibe! Do you practice daily or as a weekend reset?";
        }
        if (lower.contains("cook") || lower.contains("baking") || lower.contains("bake")) {
            return "🍳 Both enjoy cooking! What's your signature dish you'd make for a dinner date?";
        }
        if (lower.contains("art") || lower.contains("paint") || lower.contains("photo")) {
            return "🎨 Creative minds! What kind of visual art or photography inspires you the most?";
        }
        if (lower.contains("gaming") || lower.contains("game")) {
            return "🎮 Gamers at heart! PC, console, or intense board games on game night?";
        }
        if (lower.contains("music") || lower.contains("spotify") || lower.contains("production")) {
            return "🎧 Shared music taste! Which artist or indie album do you have on repeat lately?";
        }
        return String.format("🎯 You both share an interest in %s! What got you started with that?", interest);
    }

    private String generateLifestyleSpark(Profile a, Profile b, String candidateName) {
        // Dietary match
        if (a.getDietaryPref() != null && b.getDietaryPref() != null) {
            if ((a.getDietaryPref() == DietaryPreference.PURE_VEG || a.getDietaryPref() == DietaryPreference.STRICT_JAIN || a.getDietaryPref() == DietaryPreference.VEGAN) &&
                (b.getDietaryPref() == DietaryPreference.PURE_VEG || b.getDietaryPref() == DietaryPreference.STRICT_JAIN || b.getDietaryPref() == DietaryPreference.VEGAN)) {
                return "🥗 100% Veggie match! Up for finding the best butter masala dosa & chaat spots in town?";
            }
            if (a.getDietaryPref() == DietaryPreference.NON_VEG && b.getDietaryPref() == DietaryPreference.NON_VEG) {
                return "🍗 Fellow non-veg foodies! What is your undisputed top biryani spot in the city?";
            }
            if (a.getDietaryPref() == DietaryPreference.EGGETARIAN && b.getDietaryPref() == DietaryPreference.EGGETARIAN) {
                return "🍳 Eggetarian twins! Sourdough avocado-egg toast or late-night street rolls?";
            }
        }

        // Living situation
        if (a.getLivingStatus() != null && b.getLivingStatus() != null &&
            a.getLivingStatus() == LivingStatus.INDEPENDENT_FLAT && b.getLivingStatus() == LivingStatus.INDEPENDENT_FLAT) {
            return "🏠 Both enjoying independent flat life! What's your #1 house rule?";
        }

        // Vacation vibe
        if (a.getVacationPreference() != null && b.getVacationPreference() != null &&
            !a.getVacationPreference().isBlank() && a.getVacationPreference().equalsIgnoreCase(b.getVacationPreference())) {
            String vac = a.getVacationPreference();
            if (vac.toLowerCase().contains("beach")) {
                return "🏖️ Shared beach vacation vibe! Relaxing South Goa shack or scuba in the Andamans?";
            }
            if (vac.toLowerCase().contains("trek") || vac.toLowerCase().contains("mountain")) {
                return "🏔️ Mountains over beaches! Road trip up Western Ghats or Himachal expedition?";
            }
            return String.format("✈️ Both love '%s' vacations! Where's your next dream getaway?", vac);
        }

        // Drinking habit
        if (a.getDrinkingHabit() != null && b.getDrinkingHabit() != null &&
            a.getDrinkingHabit().toLowerCase().contains("non-drinker") && b.getDrinkingHabit().toLowerCase().contains("non-drinker")) {
            return "☕ Both prefer to skip alcohol! Artisanal boba or late-night hot chocolate date?";
        }

        return null;
    }
}
