package by.darkimpulsepoint.userservice.integration;

import by.darkimpulsepoint.userservice.dto.PaymentCardDto;
import by.darkimpulsepoint.userservice.dto.UserDto;
import by.darkimpulsepoint.userservice.dto.UserWithCardsDto;
import by.darkimpulsepoint.userservice.exception.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserCacheIntegrationTest extends AbstractIntegrationTest {

    private static final String USERS = "/api/v1/users";
    private static final String CARDS = "/api/v1/cards";

    @Test
    void getWithCards_putsResultIntoRedis() {
        Long userId = createUser("ivan@mail.com");
        assertThat(userCache().get(userId)).isNull();

        UserWithCardsDto response = getWithCards(userId);

        assertThat(userCache().get(userId)).isNotNull();
        assertThat(userCache().get(userId, UserWithCardsDto.class)).isEqualTo(response);
    }

    @Test
    void getWithCards_secondCallIsServedFromCache() {
        Long userId = createUser("ivan@mail.com");
        assertThat(getWithCards(userId).user().active()).isTrue();

        userRepository.updateActive(userId, false, Instant.now());

        assertThat(userRepository.findById(userId).orElseThrow().isActive()).isFalse();
        assertThat(getWithCards(userId).user().active()).isTrue();
    }

    @Test
    void getWithCards_whenUserMissing_returns404AndCachesNothing() {
        ResponseEntity<ApiError> response = rest.get().uri(USERS + "/{id}/with-cards", 999_999)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(userCache().get(999_999L)).isNull();
    }


    @Test
    void updateUser_evictsCacheAndNextReadIsFresh() {
        Long userId = createUser("ivan@mail.com");
        getWithCards(userId);

        UserDto request = new UserDto(null, "Petr", "Sidorov", LocalDate.of(1990, 1, 1),
                "ivan@mail.com", null, null, null);
        ResponseEntity<UserDto> response = rest.put().uri(USERS + "/{id}", userId)
                .body(request).retrieve().toEntity(UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userCache().get(userId)).isNull();
        assertThat(getWithCards(userId).user().name()).isEqualTo("Petr");
    }

    @Test
    void deactivateAndActivateUser_evictCache() {
        Long userId = createUser("ivan@mail.com");
        getWithCards(userId);

        rest.patch().uri(USERS + "/{id}/deactivate", userId).retrieve().toBodilessEntity();
        assertThat(userCache().get(userId)).isNull();
        assertThat(getWithCards(userId).user().active()).isFalse();

        rest.patch().uri(USERS + "/{id}/activate", userId).retrieve().toBodilessEntity();
        assertThat(userCache().get(userId)).isNull();
        assertThat(getWithCards(userId).user().active()).isTrue();
    }

    @Test
    void deleteUser_evictsCacheAndNextReadReturns404() {
        Long userId = createUser("ivan@mail.com");
        createCard(userId, "4111111111111111");
        getWithCards(userId);

        ResponseEntity<Void> response = rest.delete().uri(USERS + "/{id}", userId)
                .retrieve().toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(userCache().get(userId)).isNull();
        ResponseEntity<ApiError> after = rest.get().uri(USERS + "/{id}/with-cards", userId)
                .retrieve().toEntity(ApiError.class);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createCard_evictsCacheAndNextReadContainsCard() {
        Long userId = createUser("ivan@mail.com");
        assertThat(getWithCards(userId).cards()).isEmpty();

        createCard(userId, "4111111111111111");

        assertThat(userCache().get(userId)).isNull();
        assertThat(getWithCards(userId).cards()).extracting(PaymentCardDto::number)
                .containsExactly("4111111111111111");
    }

    @Test
    void updateCard_evictsCacheAndNextReadIsFresh() {
        Long userId = createUser("ivan@mail.com");
        Long cardId = createCard(userId, "4111111111111111");
        getWithCards(userId);

        PaymentCardDto request = new PaymentCardDto(null, null, "4999999999999999", "NEW HOLDER",
                LocalDate.now().plusYears(5), null, null, null);
        ResponseEntity<PaymentCardDto> response = rest.put().uri(CARDS + "/{id}", cardId)
                .body(request).retrieve().toEntity(PaymentCardDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userCache().get(userId)).isNull();
        assertThat(getWithCards(userId).cards()).extracting(PaymentCardDto::number)
                .containsExactly("4999999999999999");
    }

    @Test
    void deactivateAndActivateCard_evictCache() {
        Long userId = createUser("ivan@mail.com");
        Long cardId = createCard(userId, "4111111111111111");
        getWithCards(userId);

        rest.patch().uri(CARDS + "/{id}/deactivate", cardId).retrieve().toBodilessEntity();
        assertThat(userCache().get(userId)).isNull();
        assertThat(getWithCards(userId).cards().get(0).active()).isFalse();

        rest.patch().uri(CARDS + "/{id}/activate", cardId).retrieve().toBodilessEntity();
        assertThat(userCache().get(userId)).isNull();
        assertThat(getWithCards(userId).cards().get(0).active()).isTrue();
    }

    @Test
    void deleteCard_evictsCacheAndNextReadHasNoCard() {
        Long userId = createUser("ivan@mail.com");
        Long cardId = createCard(userId, "4111111111111111");
        assertThat(getWithCards(userId).cards()).hasSize(1);

        ResponseEntity<Void> response = rest.delete().uri(CARDS + "/{id}", cardId)
                .retrieve().toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(userCache().get(userId)).isNull();
        assertThat(getWithCards(userId).cards()).isEmpty();
    }

    @Test
    void changingOneUser_doesNotEvictCacheOfAnotherUser() {
        Long first = createUser("ivan@mail.com");
        Long second = createUser("petr@mail.com");
        getWithCards(first);
        getWithCards(second);

        rest.patch().uri(USERS + "/{id}/deactivate", first).retrieve().toBodilessEntity();

        assertThat(userCache().get(first)).isNull();
        assertThat(userCache().get(second)).isNotNull();
    }

    @Test
    void failedUpdate_keepsCache() {
        Long first = createUser("ivan@mail.com");
        createUser("petr@mail.com");
        getWithCards(first);

        UserDto request = new UserDto(null, "Ivan", "Petrov", LocalDate.of(1995, 5, 20),
                "petr@mail.com", null, null, null);
        ResponseEntity<ApiError> response = rest.put().uri(USERS + "/{id}", first)
                .body(request).retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(userCache().get(first)).isNotNull();
    }

    @Test
    void failedCardCreation_keepsCache() {
        Long userId = createUser("ivan@mail.com");
        createCard(userId, "4111111111111111");
        getWithCards(userId);

        ResponseEntity<ApiError> response = rest.post().uri(USERS + "/{id}/cards", userId)
                .body(cardRequest("4111111111111111"))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(userCache().get(userId)).isNotNull();
    }

    private Long createUser(String email) {
        UserDto request = new UserDto(null, "Ivan", "Petrov", LocalDate.of(1995, 5, 20), email,
                null, null, null);
        ResponseEntity<UserDto> response = rest.post().uri(USERS)
                .body(request).retrieve().toEntity(UserDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private Long createCard(Long userId, String number) {
        ResponseEntity<PaymentCardDto> response = rest.post().uri(USERS + "/{id}/cards", userId)
                .body(cardRequest(number)).retrieve().toEntity(PaymentCardDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private UserWithCardsDto getWithCards(Long userId) {
        ResponseEntity<UserWithCardsDto> response = rest.get().uri(USERS + "/{id}/with-cards", userId)
                .retrieve().toEntity(UserWithCardsDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static PaymentCardDto cardRequest(String number) {
        return new PaymentCardDto(null, null, number, "IVAN PETROV",
                LocalDate.now().plusYears(3), null, null, null);
    }
}
