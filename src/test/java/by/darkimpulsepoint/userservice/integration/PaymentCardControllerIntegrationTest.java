package by.darkimpulsepoint.userservice.integration;

import by.darkimpulsepoint.userservice.dto.PageResponse;
import by.darkimpulsepoint.userservice.dto.PaymentCardDto;
import by.darkimpulsepoint.userservice.dto.UserDto;
import by.darkimpulsepoint.userservice.entity.PaymentCard;
import by.darkimpulsepoint.userservice.entity.User;
import by.darkimpulsepoint.userservice.exception.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentCardControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String USERS = "/api/v1/users";
    private static final String CARDS = "/api/v1/cards";

    @Test
    void createCard_returns201WithLocationAndPersistsCard() {
        Long userId = createUser("Ivan", "Petrov", "ivan@mail.com");

        ResponseEntity<PaymentCardDto> response = postCard(userId, "4111111111111111");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PaymentCardDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.userId()).isEqualTo(userId);
        assertThat(body.number()).isEqualTo("4111111111111111");
        assertThat(body.active()).isTrue();
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo(CARDS + "/" + body.id());

        PaymentCard saved = cardRepository.findById(body.id()).orElseThrow();
        assertThat(saved.getNumber()).isEqualTo("4111111111111111");
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void createCard_whenUserMissing_returns404() {
        ResponseEntity<ApiError> response = rest.post().uri(USERS + "/{id}/cards", 999_999)
                .body(cardRequest("4111111111111111"))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(cardRepository.count()).isZero();
    }

    @Test
    void createCard_withExistingNumber_returns409() {
        Long first = createUser("Ivan", "Petrov", "ivan@mail.com");
        Long second = createUser("Petr", "Sidorov", "petr@mail.com");
        postCard(first, "4111111111111111");

        ResponseEntity<ApiError> response = rest.post().uri(USERS + "/{id}/cards", second)
                .body(cardRequest("4111111111111111"))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(cardRepository.count()).isEqualTo(1);
    }

    @Test
    void createCard_whenUserAlreadyHasFiveCards_returns409() {
        Long userId = createUser("Ivan", "Petrov", "ivan@mail.com");
        for (int i = 0; i < User.MAX_CARDS; i++) {
            assertThat(postCard(userId, cardNumber(i)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        ResponseEntity<ApiError> response = rest.post().uri(USERS + "/{id}/cards", userId)
                .body(cardRequest(cardNumber(User.MAX_CARDS)))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).contains(String.valueOf(User.MAX_CARDS));
        assertThat(cardRepository.countCardsByUserId(userId)).isEqualTo(User.MAX_CARDS);
    }

    @Test
    void createCard_withInvalidBody_returns400WithFieldErrors() {
        Long userId = createUser("Ivan", "Petrov", "ivan@mail.com");
        PaymentCardDto invalid = new PaymentCardDto(null, null, "12ab", "",
                LocalDate.now().minusDays(1), null, null, null);

        ResponseEntity<ApiError> response = rest.post().uri(USERS + "/{id}/cards", userId)
                .body(invalid)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().validationErrors())
                .containsKeys("number", "holder", "expirationDate");
        assertThat(cardRepository.count()).isZero();
    }

    @Test
    void createCard_whenBodyUserIdDiffersFromPath_returns400() {
        Long userId = createUser("Ivan", "Petrov", "ivan@mail.com");
        PaymentCardDto body = new PaymentCardDto(null, userId + 1, "4111111111111111", "IVAN PETROV",
                LocalDate.now().plusYears(3), null, null, null);

        ResponseEntity<ApiError> response = rest.post().uri(USERS + "/{id}/cards", userId)
                .body(body)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cardRepository.count()).isZero();
    }

    @Test
    void getCardsOfUser_returnsOnlyHisCards() {
        Long first = createUser("Ivan", "Petrov", "ivan@mail.com");
        Long second = createUser("Petr", "Sidorov", "petr@mail.com");
        postCard(first, "4111111111111111");
        postCard(first, "4222222222222222");
        postCard(second, "4333333333333333");

        ResponseEntity<List<PaymentCardDto>> response = rest.get().uri(USERS + "/{id}/cards", first)
                .retrieve().toEntity(new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(PaymentCardDto::number)
                .containsExactly("4111111111111111", "4222222222222222");
    }

    @Test
    void getCardsOfUser_whenUserMissing_returns404() {
        ResponseEntity<ApiError> response = rest.get().uri(USERS + "/{id}/cards", 999_999)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getCard_returns200() {
        Long userId = createUser("Ivan", "Petrov", "ivan@mail.com");
        PaymentCardDto created = postCard(userId, "4111111111111111").getBody();

        ResponseEntity<PaymentCardDto> response = rest.get().uri(CARDS + "/{id}", created.id())
                .retrieve().toEntity(PaymentCardDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt")
                .isEqualTo(created);
        assertThat(Duration.between(created.createdAt(), response.getBody().createdAt()).abs())
                .isLessThan(Duration.ofMillis(1));
    }

    @Test
    void getCard_whenMissing_returns404() {
        ResponseEntity<ApiError> response = rest.get().uri(CARDS + "/{id}", 999_999)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).contains("999999");
    }

    @Test
    void getCards_filtersByOwnerNameAndSurname() {
        Long ivan = createUser("Ivan", "Petrov", "ivan@mail.com");
        Long maria = createUser("Maria", "Sidorova", "maria@mail.com");
        postCard(ivan, "4111111111111111");
        postCard(ivan, "4222222222222222");
        postCard(maria, "4333333333333333");

        PageResponse<PaymentCardDto> byName = getPage(CARDS + "?name=ivan");
        PageResponse<PaymentCardDto> bySurname = getPage(CARDS + "?surname=SIDOR");
        PageResponse<PaymentCardDto> noMatch = getPage(CARDS + "?name=ivan&surname=sidor");
        PageResponse<PaymentCardDto> all = getPage(CARDS);

        assertThat(byName.content()).extracting(PaymentCardDto::number)
                .containsExactlyInAnyOrder("4111111111111111", "4222222222222222");
        assertThat(bySurname.content()).extracting(PaymentCardDto::number)
                .containsExactly("4333333333333333");
        assertThat(noMatch.content()).isEmpty();
        assertThat(all.totalElements()).isEqualTo(3);
    }

    @Test
    void getCards_supportsPagination() {
        Long userId = createUser("Ivan", "Petrov", "ivan@mail.com");
        postCard(userId, "4111111111111111");
        postCard(userId, "4222222222222222");
        postCard(userId, "4333333333333333");

        PageResponse<PaymentCardDto> page = getPage(CARDS + "?page=1&size=2&sort=id");

        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void updateCard_returns200AndPersistsChanges() {
        Long userId = createUser("Ivan", "Petrov", "ivan@mail.com");
        PaymentCardDto created = postCard(userId, "4111111111111111").getBody();
        PaymentCardDto request = new PaymentCardDto(null, null, "4999999999999999", "NEW HOLDER",
                LocalDate.now().plusYears(5), null, null, null);

        ResponseEntity<PaymentCardDto> response = rest.put().uri(CARDS + "/{id}", created.id())
                .body(request)
                .retrieve().toEntity(PaymentCardDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().number()).isEqualTo("4999999999999999");
        assertThat(response.getBody().userId()).isEqualTo(userId);
        PaymentCard saved = cardRepository.findById(created.id()).orElseThrow();
        assertThat(saved.getNumber()).isEqualTo("4999999999999999");
        assertThat(saved.getHolder()).isEqualTo("NEW HOLDER");
    }

    @Test
    void updateCard_keepingOwnNumber_returns200() {
        Long userId = createUser("Ivan", "Petrov", "ivan@mail.com");
        PaymentCardDto created = postCard(userId, "4111111111111111").getBody();

        ResponseEntity<PaymentCardDto> response = rest.put().uri(CARDS + "/{id}", created.id())
                .body(cardRequest("4111111111111111"))
                .retrieve().toEntity(PaymentCardDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateCard_whenMissing_returns404() {
        ResponseEntity<ApiError> response = rest.put().uri(CARDS + "/{id}", 999_999)
                .body(cardRequest("4111111111111111"))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateCard_withNumberOfAnotherCard_returns409() {
        Long userId = createUser("Ivan", "Petrov", "ivan@mail.com");
        postCard(userId, "4111111111111111");
        PaymentCardDto second = postCard(userId, "4222222222222222").getBody();

        ResponseEntity<ApiError> response = rest.put().uri(CARDS + "/{id}", second.id())
                .body(cardRequest("4111111111111111"))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(cardRepository.findById(second.id()).orElseThrow().getNumber())
                .isEqualTo("4222222222222222");
    }

    @Test
    void updateCard_whenBodyIdDiffersFromPath_returns400() {
        Long userId = createUser("Ivan", "Petrov", "ivan@mail.com");
        PaymentCardDto created = postCard(userId, "4111111111111111").getBody();
        PaymentCardDto body = new PaymentCardDto(created.id() + 1, null, "4111111111111111", "IVAN PETROV",
                LocalDate.now().plusYears(3), null, null, null);

        ResponseEntity<ApiError> response = rest.put().uri(CARDS + "/{id}", created.id())
                .body(body)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deactivateAndActivateCard_return204AndChangeFlag() {
        Long userId = createUser("Ivan", "Petrov", "ivan@mail.com");
        PaymentCardDto created = postCard(userId, "4111111111111111").getBody();

        ResponseEntity<Void> deactivated = rest.patch().uri(CARDS + "/{id}/deactivate", created.id())
                .retrieve().toBodilessEntity();
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(cardRepository.findById(created.id()).orElseThrow().isActive()).isFalse();

        ResponseEntity<Void> activated = rest.patch().uri(CARDS + "/{id}/activate", created.id())
                .retrieve().toBodilessEntity();
        assertThat(activated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(cardRepository.findById(created.id()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void activateAndDeactivateCard_whenMissing_return404() {
        ResponseEntity<ApiError> activate = rest.patch().uri(CARDS + "/{id}/activate", 999_999)
                .retrieve().toEntity(ApiError.class);
        ResponseEntity<ApiError> deactivate = rest.patch().uri(CARDS + "/{id}/deactivate", 999_999)
                .retrieve().toEntity(ApiError.class);

        assertThat(activate.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deactivate.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteCard_returns204AndKeepsUser() {
        Long userId = createUser("Ivan", "Petrov", "ivan@mail.com");
        PaymentCardDto created = postCard(userId, "4111111111111111").getBody();

        ResponseEntity<Void> response = rest.delete().uri(CARDS + "/{id}", created.id())
                .retrieve().toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(cardRepository.existsById(created.id())).isFalse();
        assertThat(userRepository.existsById(userId)).isTrue();
    }

    @Test
    void deleteCard_whenMissing_returns404() {
        ResponseEntity<ApiError> response = rest.delete().uri(CARDS + "/{id}", 999_999)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private Long createUser(String name, String surname, String email) {
        UserDto request = new UserDto(null, name, surname, LocalDate.of(1995, 5, 20), email,
                null, null, null);
        ResponseEntity<UserDto> response = rest.post().uri(USERS)
                .body(request)
                .retrieve().toEntity(UserDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private ResponseEntity<PaymentCardDto> postCard(Long userId, String number) {
        return rest.post().uri(USERS + "/{id}/cards", userId)
                .body(cardRequest(number))
                .retrieve().toEntity(PaymentCardDto.class);
    }

    private PageResponse<PaymentCardDto> getPage(String uri) {
        ResponseEntity<PageResponse<PaymentCardDto>> response = rest.get().uri(uri)
                .retrieve().toEntity(new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static PaymentCardDto cardRequest(String number) {
        return new PaymentCardDto(null, null, number, "IVAN PETROV",
                LocalDate.now().plusYears(3), null, null, null);
    }

    private static String cardNumber(int index) {
        return "40000000000000" + String.format("%02d", index);
    }
}