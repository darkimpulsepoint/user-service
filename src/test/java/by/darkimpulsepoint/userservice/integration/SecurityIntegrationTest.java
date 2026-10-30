package by.darkimpulsepoint.userservice.integration;

import by.darkimpulsepoint.userservice.dto.PaymentCardDto;
import by.darkimpulsepoint.userservice.dto.UserDto;
import by.darkimpulsepoint.userservice.exception.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String USERS = "/api/v1/users";
    private static final String CARDS = "/api/v1/cards";

    @Test
    void request_withoutToken_returns401() {
        Long userId = createUser("ivan@mail.com");

        ResponseEntity<ApiError> response = anonymous.get().uri(USERS + "/{id}", userId)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(401);
        assertThat(response.getBody().path()).isEqualTo(USERS + "/" + userId);
    }

    @Test
    void request_withInvalidSignature_returns401() {
        Long userId = createUser("ivan@mail.com");
        String token = userToken(userId);
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        ResponseEntity<ApiError> response = clientWithToken(tampered).get().uri(USERS + "/{id}", userId)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).isEqualTo("Invalid or expired access token");
    }

    @Test
    void request_withExpiredToken_returns401() {
        Long userId = createUser("ivan@mail.com");
        String expired = token(userId, "USER", "access", Duration.ofMinutes(-5));

        ResponseEntity<ApiError> response = clientWithToken(expired).get().uri(USERS + "/{id}", userId)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void request_withRefreshToken_returns401() {
        Long userId = createUser("ivan@mail.com");
        String refresh = token(userId, "USER", "refresh", Duration.ofDays(7));

        ResponseEntity<ApiError> response = clientWithToken(refresh).get().uri(USERS + "/{id}", userId)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createUser_withoutToken_isAllowed() {
        ResponseEntity<UserDto> response = anonymous.post().uri(USERS)
                .body(userRequest("ivan@mail.com"))
                .retrieve().toEntity(UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void user_canReadAndUpdateOwnProfile() {
        Long userId = createUser("ivan@mail.com");
        RestClient user = clientWithToken(userToken(userId));

        ResponseEntity<UserDto> byId = user.get().uri(USERS + "/{id}", userId)
                .retrieve().toEntity(UserDto.class);
        ResponseEntity<UserDto> me = user.get().uri(USERS + "/me")
                .retrieve().toEntity(UserDto.class);
        ResponseEntity<UserDto> updated = user.put().uri(USERS + "/{id}", userId)
                .body(userRequest("ivan.new@mail.com"))
                .retrieve().toEntity(UserDto.class);

        assertThat(byId.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().id()).isEqualTo(userId);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().email()).isEqualTo("ivan.new@mail.com");
    }

    @Test
    void user_cannotAccessAnotherUser() {
        Long ownerId = createUser("ivan@mail.com");
        Long otherId = createUser("petr@mail.com");
        RestClient user = clientWithToken(userToken(ownerId));

        ResponseEntity<ApiError> get = user.get().uri(USERS + "/{id}", otherId)
                .retrieve().toEntity(ApiError.class);
        ResponseEntity<ApiError> update = user.put().uri(USERS + "/{id}", otherId)
                .body(userRequest("hacked@mail.com"))
                .retrieve().toEntity(ApiError.class);
        ResponseEntity<ApiError> cards = user.get().uri(USERS + "/{id}/cards", otherId)
                .retrieve().toEntity(ApiError.class);

        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get.getBody().status()).isEqualTo(403);
        assertThat(get.getBody().message()).isEqualTo("Access denied");
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(cards.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userRepository.findById(otherId).orElseThrow().getEmail()).isEqualTo("petr@mail.com");
    }

    @Test
    void user_cannotCallAdminEndpoints() {
        Long userId = createUser("ivan@mail.com");
        RestClient user = clientWithToken(userToken(userId));

        assertThat(user.get().uri(USERS).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(user.get().uri(CARDS).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(user.patch().uri(USERS + "/{id}/deactivate", userId).retrieve().toBodilessEntity()
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(user.delete().uri(USERS + "/{id}", userId).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userRepository.findById(userId).orElseThrow().isActive()).isTrue();
    }

    @Test
    void user_canManageOwnCards_butNotCardsOfOthers() {
        Long ownerId = createUser("ivan@mail.com");
        Long otherId = createUser("petr@mail.com");
        RestClient owner = clientWithToken(userToken(ownerId));
        RestClient other = clientWithToken(userToken(otherId));

        ResponseEntity<PaymentCardDto> created = owner.post().uri(USERS + "/{id}/cards", ownerId)
                .body(cardRequest())
                .retrieve().toEntity(PaymentCardDto.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long cardId = created.getBody().id();

        assertThat(owner.get().uri(CARDS + "/{id}", cardId).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(other.get().uri(CARDS + "/{id}", cardId).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(other.delete().uri(CARDS + "/{id}", cardId).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(other.post().uri(USERS + "/{id}/cards", ownerId).body(cardRequest())
                .retrieve().toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(cardRepository.existsById(cardId)).isTrue();

        assertThat(owner.delete().uri(CARDS + "/{id}", cardId).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void me_withAdminTokenWithoutUserId_returns403() {
        ResponseEntity<ApiError> response = rest.get().uri(USERS + "/me")
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private Long createUser(String email) {
        ResponseEntity<UserDto> response = anonymous.post().uri(USERS)
                .body(userRequest(email))
                .retrieve().toEntity(UserDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private static UserDto userRequest(String email) {
        return new UserDto(null, "Ivan", "Petrov", LocalDate.of(1995, 5, 20), email, null, null, null);
    }

    private static PaymentCardDto cardRequest() {
        return new PaymentCardDto(null, null, "4111111111111111", "IVAN PETROV",
                LocalDate.now().plusYears(3), null, null, null);
    }
}
