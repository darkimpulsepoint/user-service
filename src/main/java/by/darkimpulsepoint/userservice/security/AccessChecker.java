package by.darkimpulsepoint.userservice.security;

import by.darkimpulsepoint.userservice.repository.PaymentCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("access")
@RequiredArgsConstructor
public class AccessChecker {

    private final PaymentCardRepository cardRepository;

    public boolean isOwner(Long userId) {
        return userId != null && SecurityUtils.findCurrentUserId()
                .map(userId::equals)
                .orElse(false);
    }

    public boolean ownsCard(Long cardId) {
        return cardId != null && SecurityUtils.findCurrentUserId()
                .map(userId -> cardRepository.existsByIdAndUserId(cardId, userId))
                .orElse(false);
    }
}
