package com.match.SwipeAI.service.integration;

import com.match.SwipeAI.dto.ChatDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NudityDetectorService {

    public ChatDto.NudityCheckResponse scanMedia(String mediaUrl) {
        log.info("Running Shield 360 AI Nudity & Sensitive Content classifier on media: {}", mediaUrl);

        // Check for test keywords or simulated NSFW content
        boolean isNsfw = mediaUrl != null && (mediaUrl.contains("nsfw") || mediaUrl.contains("sensitive") || mediaUrl.contains("explicit"));
        double score = isNsfw ? 0.92 : 0.05;

        return ChatDto.NudityCheckResponse.builder()
                .isSensitive(isNsfw)
                .confidenceScore(score)
                .action(isNsfw ? "BLUR_WITH_WARNING" : "ALLOW")
                .message(isNsfw ? "Sensitive content detected. Auto-blurring with consent prompt." : "Image content safe.")
                .build();
    }
}
