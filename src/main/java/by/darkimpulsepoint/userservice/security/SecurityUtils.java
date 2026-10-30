package by.darkimpulsepoint.userservice.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<Long> findCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return Optional.empty();
        }
        Object userId = token.getToken().getClaims().get(JwtClaimNames.USER_ID);
        return userId instanceof Number number ? Optional.of(number.longValue()) : Optional.empty();
    }

    public static Long getCurrentUserId() {
        return findCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Access token does not contain user id"));
    }
}
