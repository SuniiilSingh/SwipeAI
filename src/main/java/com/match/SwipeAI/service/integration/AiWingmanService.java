package com.match.SwipeAI.service.integration;

import com.match.SwipeAI.config.FeatureFlagsProperties;
import com.match.SwipeAI.model.Profile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Wingman Conversational Spark Service.
 * If live OpenAI integration is disabled, utilizes local Hinglish heuristics
 * to generate high-converting opening lines for instant testing without API costs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiWingmanService {

    private final FeatureFlagsProperties properties;

    /*
     * =========================================================================
     * AI WINGMAN (OPENAI / HEURISTIC MOCK) - COMMENTED OUT AS OF NOW
     * Replaced by Alternative 1: MutualChemistrySparksEngine (Data-Driven Matcher)
     * =========================================================================
     *
    public List<String> generateConversationSparks(Profile viewerProfile, Profile candidateProfile) {
        boolean live = properties.getFeatures().getAiWingman().isEnabled();

        if (live) {
            log.info("[FEATURE_FLAG: OpenAI Wingman LIVE] Invoking OpenAI API for {} and {}",
                    viewerProfile.getDisplayName(), candidateProfile.getDisplayName());
            / *
             * LIVE INTEGRATION SKELETON:
             * RestTemplate restTemplate = new RestTemplate();
             * HttpHeaders headers = new HttpHeaders();
             * headers.setBearerAuth(properties.getFeatures().getAiWingman().getApiKey());
             * headers.setContentType(MediaType.APPLICATION_JSON);
             * Map<String, Object> body = Map.of(
             *     "model", properties.getFeatures().getAiWingman().getModel(),
             *     "messages", List.of(
             *         Map.of("role", "system", "content", "You are an Indian dating AI Wingman. Generate 3 witty, charming, respectful Hinglish conversation starters under 20 words each."),
             *         Map.of("role", "user", "content", "Candidate name: " + candidateProfile.getDisplayName() + ", Bio: " + candidateProfile.getBio() + ", Voice note: " + candidateProfile.getVoicePromptText())
             *     )
             * );
             * ResponseEntity<OpenAiChatResponse> res = restTemplate.postForEntity("https://api.openai.com/v1/chat/completions", new HttpEntity<>(body, headers), OpenAiChatResponse.class);
             * /
        } else {
            log.info("[MOCK TESTING ENVIRONMENT] OpenAI disabled. Using local Hinglish spark engine for {}", candidateProfile.getDisplayName());
        }

        // High-converting local contextual sparks for testing
        List<String> sparks = new ArrayList<>();
        String candidateName = candidateProfile.getDisplayName();

        if (candidateProfile.getVoicePromptText() != null && !candidateProfile.getVoicePromptText().isEmpty()) {
            sparks.add(String.format("Heard your voice note about '%s' — %s, I have strong thoughts on that!",
                    candidateProfile.getVoicePromptText(), candidateName));
        } else {
            sparks.add(String.format("Hey %s, Rameshwaram Cafe vs CTR — which side of the Indiranagar dosa debate are you on?", candidateName));
        }

        if (candidateProfile.getCity() != null) {
            sparks.add(String.format("If we were grabbing cutting chai in %s right now, what's your go-to tea spot?", candidateProfile.getCity()));
        } else {
            sparks.add("Elaichi chai or ginger chai on a rainy weekend evening?");
        }

        if (candidateProfile.getCompany() != null && !candidateProfile.getCompany().isEmpty()) {
            sparks.add(String.format("Working at %s must have crazy stories! What's the best perk you actually use?", candidateProfile.getCompany()));
        } else {
            sparks.add("10/10 vibe on your profile! What's the soundtrack to your current week?");
        }

        return sparks;
    }
    */

    /**
     * Deprecated method stub preserved for binary compatibility; calls to this
     * are redirected to MutualChemistrySparksEngine in MatchService.
     */
    public List<String> generateConversationSparks(Profile viewerProfile, Profile candidateProfile) {
        log.info("[AI WINGMAN INACTIVE] Calls are redirected to MutualChemistrySparksEngine");
        return List.of(
                "Hey " + (candidateProfile != null ? candidateProfile.getDisplayName() : "there") + "! What's the soundtrack to your week?",
                "Filter coffee or cutting chai on a rainy evening?",
                "What's one thing that always makes you smile?"
        );
    }
}
