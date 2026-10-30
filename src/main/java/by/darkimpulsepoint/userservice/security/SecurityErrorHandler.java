package by.darkimpulsepoint.userservice.security;

import by.darkimpulsepoint.userservice.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Writes security errors raised in the filter chain (before a controller is reached) as {@link ApiError}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        log.debug("Authentication failed for {}: {}", request.getRequestURI(), ex.getMessage());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, request, HttpStatus.UNAUTHORIZED, authenticationMessage(ex));
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        log.debug("Access denied for {}: {}", request.getRequestURI(), ex.getMessage());
        write(response, request, HttpStatus.FORBIDDEN, "Access denied");
    }

    public static String authenticationMessage(AuthenticationException ex) {
        return ex instanceof OAuth2AuthenticationException
                ? "Invalid or expired access token"
                : "Authentication is required to access this resource";
    }

    private void write(HttpServletResponse response,
                       HttpServletRequest request,
                       HttpStatus status,
                       String message) throws IOException {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), Map.of());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
