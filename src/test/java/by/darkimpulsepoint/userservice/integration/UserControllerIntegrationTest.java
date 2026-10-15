package by.darkimpulsepoint.userservice.integration;

import by.darkimpulsepoint.userservice.dto.PageResponse;
import by.darkimpulsepoint.userservice.dto.PaymentCardDto;
import by.darkimpulsepoint.userservice.dto.UserDto;
import by.darkimpulsepoint.userservice.dto.UserWithCardsDto;
import by.darkimpulsepoint.userservice.entity.User;
import by.darkimpulsepoint.userservice.exception.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String USERS = "/api/v1/users";

    @Test
    void createUser_returns201WithLocationAndPersistsUser() {
        ResponseEntity<UserDto> response = createUser("Ivan", "Petrov", "ivan@mail.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UserDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.name()).isEqualTo("Ivan");
        assertThat(body.email()).isEqualTo("ivan@mail.com");
        assertThat(body.active()).isTrue();
        assertThat(body.createdAt()).isNotNull();
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo(USERS + "/" + body.id());

        User saved = userRepository.findById(body.id()).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("ivan@mail.com");
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void createUser_withExistingEmail_returns409() {
        createUser("Ivan", "Petrov", "ivan@mail.com");

        ResponseEntity<ApiError> response = rest.post().uri(USERS)
                .body(userRequest("Petr", "Sidorov", "ivan@mail.com"))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().message()).contains("ivan@mail.com");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void createUser_withInvalidBody_returns400WithFieldErrors() {
        UserDto invalid = new UserDto(null, " ", "Petrov", LocalDate.now().plusDays(1),
                "not-an-email", null, null, null);

        ResponseEntity<ApiError> response = rest.post().uri(USERS)
                .body(invalid)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().validationErrors())
                .containsKeys("name", "birthDate", "email");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void createUser_withMalformedJson_returns400() {
        ResponseEntity<ApiError> response = rest.post().uri(USERS)
                .header("Content-Type", "application/json")
                .body("{not json")
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
    }


    @Test
    void getUser_returns200() {
        UserDto created = createUser("Ivan", "Petrov", "ivan@mail.com").getBody();

        ResponseEntity<UserDto> response = rest.get().uri(USERS + "/{id}", created.id())
                .retrieve().toEntity(UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt")
                .isEqualTo(created);
        assertThat(Duration.between(created.createdAt(), response.getBody().createdAt()).abs())
                .isLessThan(Duration.ofMillis(1));
    }

    @Test
    void getUser_whenMissing_returns404() {
        ResponseEntity<ApiError> response = rest.get().uri(USERS + "/{id}", 999_999)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("999999");
        assertThat(response.getBody().path()).isEqualTo(USERS + "/999999");
    }

    @Test
    void getUser_withNonNumericId_returns400() {
        ResponseEntity<ApiError> response = rest.get().uri(USERS + "/abc")
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getUsers_filtersByNameAndSurnameCaseInsensitively() {
        createUser("Ivan", "Petrov", "ivan@mail.com");
        createUser("Ivanna", "Sidorova", "ivanna@mail.com");
        createUser("Maria", "Petrova", "maria@mail.com");

        PageResponse<UserDto> byName = getPage(USERS + "?name=IVAN");
        PageResponse<UserDto> byBoth = getPage(USERS + "?name=ivan&surname=petr");
        PageResponse<UserDto> all = getPage(USERS);

        assertThat(byName.content()).extracting(UserDto::email)
                .containsExactlyInAnyOrder("ivan@mail.com", "ivanna@mail.com");
        assertThat(byBoth.content()).extracting(UserDto::email).containsExactly("ivan@mail.com");
        assertThat(all.totalElements()).isEqualTo(3);
    }

    @Test
    void getUsers_supportsPaginationAndSorting() {
        createUser("Anna", "A", "a@mail.com");
        createUser("Boris", "B", "b@mail.com");
        createUser("Clara", "C", "c@mail.com");

        PageResponse<UserDto> page = getPage(USERS + "?page=0&size=2&sort=name,desc");

        assertThat(page.content()).extracting(UserDto::name).containsExactly("Clara", "Boris");
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void updateUser_returns200AndPersistsChanges() {
        UserDto created = createUser("Ivan", "Petrov", "ivan@mail.com").getBody();

        ResponseEntity<UserDto> response = rest.put().uri(USERS + "/{id}", created.id())
                .body(userRequest("Petr", "Sidorov", "petr@mail.com"))
                .retrieve().toEntity(UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("Petr");
        User saved = userRepository.findById(created.id()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Petr");
        assertThat(saved.getSurname()).isEqualTo("Sidorov");
        assertThat(saved.getEmail()).isEqualTo("petr@mail.com");
        assertThat(saved.getUpdatedAt()).isAfter(created.updatedAt());
    }

    @Test
    void updateUser_keepingOwnEmail_returns200() {
        UserDto created = createUser("Ivan", "Petrov", "ivan@mail.com").getBody();

        ResponseEntity<UserDto> response = rest.put().uri(USERS + "/{id}", created.id())
                .body(userRequest("Ivan", "Changed", "ivan@mail.com"))
                .retrieve().toEntity(UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().surname()).isEqualTo("Changed");
    }

    @Test
    void updateUser_whenMissing_returns404() {
        ResponseEntity<ApiError> response = rest.put().uri(USERS + "/{id}", 999_999)
                .body(userRequest("Ivan", "Petrov", "ivan@mail.com"))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateUser_withEmailOfAnotherUser_returns409() {
        createUser("Ivan", "Petrov", "ivan@mail.com");
        UserDto second = createUser("Petr", "Sidorov", "petr@mail.com").getBody();

        ResponseEntity<ApiError> response = rest.put().uri(USERS + "/{id}", second.id())
                .body(userRequest("Petr", "Sidorov", "ivan@mail.com"))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(userRepository.findById(second.id()).orElseThrow().getEmail()).isEqualTo("petr@mail.com");
    }

    @Test
    void updateUser_whenBodyIdDiffersFromPath_returns400() {
        UserDto created = createUser("Ivan", "Petrov", "ivan@mail.com").getBody();
        UserDto body = new UserDto(created.id() + 1, "Ivan", "Petrov", LocalDate.of(1995, 5, 20),
                "ivan@mail.com", null, null, null);

        ResponseEntity<ApiError> response = rest.put().uri(USERS + "/{id}", created.id())
                .body(body)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateUser_withInvalidBody_returns400() {
        UserDto created = createUser("Ivan", "Petrov", "ivan@mail.com").getBody();

        ResponseEntity<ApiError> response = rest.put().uri(USERS + "/{id}", created.id())
                .body(userRequest("", "Petrov", "ivan@mail.com"))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().validationErrors()).containsKey("name");
    }

    @Test
    void deactivateAndActivateUser_return204AndChangeFlag() {
        UserDto created = createUser("Ivan", "Petrov", "ivan@mail.com").getBody();

        ResponseEntity<Void> deactivated = rest.patch().uri(USERS + "/{id}/deactivate", created.id())
                .retrieve().toBodilessEntity();
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(userRepository.findById(created.id()).orElseThrow().isActive()).isFalse();

        ResponseEntity<Void> activated = rest.patch().uri(USERS + "/{id}/activate", created.id())
                .retrieve().toBodilessEntity();
        assertThat(activated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(userRepository.findById(created.id()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void activateAndDeactivateUser_whenMissing_return404() {
        ResponseEntity<ApiError> activate = rest.patch().uri(USERS + "/{id}/activate", 999_999)
                .retrieve().toEntity(ApiError.class);
        ResponseEntity<ApiError> deactivate = rest.patch().uri(USERS + "/{id}/deactivate", 999_999)
                .retrieve().toEntity(ApiError.class);

        assertThat(activate.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deactivate.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteUser_returns204AndRemovesUserWithCards() {
        UserDto created = createUser("Ivan", "Petrov", "ivan@mail.com").getBody();
        createCard(created.id(), "4111111111111111");
        assertThat(cardRepository.count()).isEqualTo(1);

        ResponseEntity<Void> response = rest.delete().uri(USERS + "/{id}", created.id())
                .retrieve().toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(userRepository.existsById(created.id())).isFalse();
        assertThat(cardRepository.count()).isZero();
    }

    @Test
    void deleteUser_whenMissing_returns404() {
        ResponseEntity<ApiError> response = rest.delete().uri(USERS + "/{id}", 999_999)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getUserWithCards_returnsUserAndHisCardsOnly() {
        UserDto first = createUser("Ivan", "Petrov", "ivan@mail.com").getBody();
        UserDto second = createUser("Petr", "Sidorov", "petr@mail.com").getBody();
        createCard(first.id(), "4111111111111111");
        createCard(first.id(), "4222222222222222");
        createCard(second.id(), "4333333333333333");

        ResponseEntity<UserWithCardsDto> response = rest.get()
                .uri(USERS + "/{id}/with-cards", first.id())
                .retrieve().toEntity(UserWithCardsDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().user().id()).isEqualTo(first.id());
        assertThat(response.getBody().cards()).extracting(PaymentCardDto::number)
                .containsExactly("4111111111111111", "4222222222222222");
    }

    @Test
    void getUserWithCards_whenUserHasNoCards_returnsEmptyList() {
        UserDto created = createUser("Ivan", "Petrov", "ivan@mail.com").getBody();

        ResponseEntity<UserWithCardsDto> response = rest.get()
                .uri(USERS + "/{id}/with-cards", created.id())
                .retrieve().toEntity(UserWithCardsDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().cards()).isEmpty();
    }

    @Test
    void getUserWithCards_whenMissing_returns404() {
        ResponseEntity<ApiError> response = rest.get().uri(USERS + "/{id}/with-cards", 999_999)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<UserDto> createUser(String name, String surname, String email) {
        return rest.post().uri(USERS)
                .body(userRequest(name, surname, email))
                .retrieve().toEntity(UserDto.class);
    }

    private void createCard(Long userId, String number) {
        PaymentCardDto request = new PaymentCardDto(null, null, number, "IVAN PETROV",
                LocalDate.now().plusYears(3), null, null, null);
        ResponseEntity<PaymentCardDto> response = rest.post().uri(USERS + "/{id}/cards", userId)
                .body(request)
                .retrieve().toEntity(PaymentCardDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private PageResponse<UserDto> getPage(String uri) {
        ResponseEntity<PageResponse<UserDto>> response = rest.get().uri(uri)
                .retrieve().toEntity(new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static UserDto userRequest(String name, String surname, String email) {
        return new UserDto(null, name, surname, LocalDate.of(1995, 5, 20), email, null, null, null);
    }
}