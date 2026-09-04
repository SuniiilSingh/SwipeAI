package com.match.SwipeAI.service.engine;

import com.match.SwipeAI.model.Match;
import com.match.SwipeAI.model.User;
import com.match.SwipeAI.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchKarmaService {

    private final UserRepository userRepository;

    @Transactional
    public void processMatchExpiry(Match match) {
        log.info("Processing Ghost-Buster Karma penalty on expired match: {}", match.getId());
        // Penalty applied to initiator if zero messages were exchanged
        if (match.getMessagesCount() == 0 && match.getInitiatorId() != null) {
            userRepository.findById(match.getInitiatorId()).ifPresent(initiator -> {
                int oldKarma = initiator.getKarmaScore() != null ? initiator.getKarmaScore() : 100;
                int newKarma = Math.max(0, oldKarma - 8); // -8 ghosting penalty
                initiator.setKarmaScore(newKarma);
                userRepository.save(initiator);
                log.warn("Applied Ghosting Penalty (-8) to user {}. New Karma: {}", initiator.getId(), newKarma);
            });
        }
    }

    @Transactional
    public void rewardActiveConversation(UUID userAId, UUID userBId) {
        // Reward both users +3 karma for meaningful conversation milestone (>= 4 messages)
        adjustKarma(userAId, 3);
        adjustKarma(userBId, 3);
    }

    @Transactional
    public void penaltyHarassment(UUID reportedUserId) {
        // Immediate -50 penalty and shadowban flagging
        adjustKarma(reportedUserId, -50);
    }

    private void adjustKarma(UUID userId, int delta) {
        userRepository.findById(userId).ifPresent(user -> {
            int current = user.getKarmaScore() != null ? user.getKarmaScore() : 100;
            int updated = Math.min(200, Math.max(0, current + delta));
            user.setKarmaScore(updated);
            userRepository.save(user);
            log.info("Adjusted Karma for user {} by {}. New Karma: {}", userId, delta, updated);
        });
    }
}
