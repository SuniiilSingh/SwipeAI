package com.match.SwipeAI.service.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.SwipeAI.dto.MatchDto;
import com.match.SwipeAI.enums.MatchStatus;
import com.match.SwipeAI.model.Match;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IcebreakerEngine {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public MatchDto.IcebreakerQuizDto createInitialQuiz() {
        return MatchDto.IcebreakerQuizDto.builder()
                .quizId("quiz_sunday_vibe")
                .title("10s Rapid-Fire Icebreaker")
                .question("Your Ultimate Sunday Vibe:")
                .options(List.of(
                        "Filter Coffee & Dosa crawl in Indiranagar",
                        "Sleep until 2 PM & binge true-crime podcasts",
                        "Spontaneous road trip / long drive to Nandi Hills"
                ))
                .userAAnswer(0) // Default seed option or pending
                .userBAnswer(null)
                .isCompleted(false)
                .isMutualAgreement(false)
                .wingmanRecommendation("Pick your honest choice! Once both answer, chat lounge unlocks.")
                .build();
    }

    public MatchDto.IcebreakerQuizDto parseQuizData(String json) {
        if (json == null || json.isBlank()) {
            return createInitialQuiz();
        }
        try {
            return objectMapper.readValue(json, MatchDto.IcebreakerQuizDto.class);
        } catch (Exception e) {
            log.error("Failed to parse icebreaker quiz json", e);
            return createInitialQuiz();
        }
    }

    public String serializeQuizData(MatchDto.IcebreakerQuizDto quiz) {
        try {
            return objectMapper.writeValueAsString(quiz);
        } catch (Exception e) {
            return "{}";
        }
    }
}
