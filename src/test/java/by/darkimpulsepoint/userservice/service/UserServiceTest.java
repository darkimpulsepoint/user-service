package by.darkimpulsepoint.userservice.service;

import by.darkimpulsepoint.userservice.dto.PaymentCardDto;
import by.darkimpulsepoint.userservice.dto.UserDto;
import by.darkimpulsepoint.userservice.dto.UserWithCardsDto;
import by.darkimpulsepoint.userservice.entity.PaymentCard;
import by.darkimpulsepoint.userservice.entity.User;
import by.darkimpulsepoint.userservice.exception.DuplicateEntityException;
import by.darkimpulsepoint.userservice.exception.ResourceNotFoundException;
import by.darkimpulsepoint.userservice.mapper.PaymentCardMapper;
import by.darkimpulsepoint.userservice.mapper.UserMapper;
import by.darkimpulsepoint.userservice.repository.PaymentCardRepository;
import by.darkimpulsepoint.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "ivan@mail.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PaymentCardRepository cardRepository;
    @Mock
    private PaymentCardMapper cardMapper;

    @InjectMocks
    private UserService userService;

    @Test
    void create_whenEmailIsFree_savesUserAndReturnsDto() {
        UserDto request = userDto(null);
        UserDto response = userDto(USER_ID);
        User entity = new User();
        User saved = user(USER_ID);

        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(entity);
        when(userRepository.save(entity)).thenReturn(saved);
        when(userMapper.toDto(saved)).thenReturn(response);

        UserDto result = userService.create(request);

        assertThat(result).isEqualTo(response);
        verify(userRepository).save(entity);
    }

    @Test
    void create_whenEmailExists_throwsDuplicateEntityException() {
        UserDto request = userDto(null);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessageContaining(EMAIL);

        verify(userRepository, never()).save(any());
    }

    @Test
    void getById_whenUserExists_returnsDto() {
        User user = user(USER_ID);
        UserDto dto = userDto(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(dto);

        assertThat(userService.getById(USER_ID)).isEqualTo(dto);
    }

    @Test
    void getById_whenUserMissing_throwsResourceNotFoundException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(USER_ID));
    }

    @Test
    void getByIdWithCards_whenUserExists_returnsUserWithAllCards() {
        User user = user(USER_ID);
        PaymentCard first = new PaymentCard();
        PaymentCard second = new PaymentCard();
        UserDto userDto = userDto(USER_ID);
        PaymentCardDto firstDto = cardDto(10L);
        PaymentCardDto secondDto = cardDto(11L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(userDto);
        when(cardRepository.findCardsByUserId(USER_ID)).thenReturn(List.of(first, second));
        when(cardMapper.toDto(first)).thenReturn(firstDto);
        when(cardMapper.toDto(second)).thenReturn(secondDto);

        UserWithCardsDto result = userService.getByIdWithCards(USER_ID);

        assertThat(result.user()).isEqualTo(userDto);
        assertThat(result.cards()).containsExactly(firstDto, secondDto);
    }

    @Test
    void getByIdWithCards_whenUserHasNoCards_returnsEmptyCardList() {
        User user = user(USER_ID);
        UserDto userDto = userDto(USER_ID);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(userDto);
        when(cardRepository.findCardsByUserId(USER_ID)).thenReturn(List.of());

        UserWithCardsDto result = userService.getByIdWithCards(USER_ID);

        assertThat(result.user()).isEqualTo(userDto);
        assertThat(result.cards()).isEmpty();
    }

    @Test
    void getByIdWithCards_whenUserMissing_throwsAndSkipsCardLookup() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getByIdWithCards(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(cardRepository);
    }

    @Test
    void getAll_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 10);
        User user = user(USER_ID);
        UserDto dto = userDto(USER_ID);
        Page<User> page = new PageImpl<>(List.of(user), pageable, 1);

        when(userRepository.findAll(ArgumentMatchers.<Specification<User>>any(), eq(pageable)))
                .thenReturn(page);
        when(userMapper.toDto(user)).thenReturn(dto);

        Page<UserDto> result = userService.getAll("Ivan", "Petrov", pageable);

        assertThat(result.getContent()).containsExactly(dto);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getAll_whenNothingFound_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(ArgumentMatchers.<Specification<User>>any(), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        assertThat(userService.getAll(null, null, pageable).getContent()).isEmpty();
    }

    @Test
    void update_whenUserExistsAndEmailFree_updatesAndReturnsDto() {
        UserDto request = userDto(null);
        UserDto response = userDto(USER_ID);
        User user = user(USER_ID);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot(EMAIL, USER_ID)).thenReturn(false);
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(response);

        UserDto result = userService.update(USER_ID, request);

        assertThat(result).isEqualTo(response);
        verify(userMapper).updateEntity(request, user);
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void update_whenUserMissing_throwsResourceNotFoundException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(USER_ID, userDto(null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_whenEmailBelongsToAnotherUser_throwsDuplicateEntityException() {
        UserDto request = userDto(null);
        User user = user(USER_ID);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot(EMAIL, USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> userService.update(USER_ID, request))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessageContaining(EMAIL);

        verify(userMapper, never()).updateEntity(any(), any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void activate_whenUserExists_setsActiveTrue() {
        when(userRepository.updateActive(eq(USER_ID), eq(true), any(Instant.class))).thenReturn(1);

        userService.activate(USER_ID);

        verify(userRepository).updateActive(eq(USER_ID), eq(true), any(Instant.class));
    }

    @Test
    void activate_whenUserMissing_throwsResourceNotFoundException() {
        when(userRepository.updateActive(eq(USER_ID), eq(true), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> userService.activate(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(USER_ID));
    }

    @Test
    void deactivate_whenUserExists_setsActiveFalse() {
        when(userRepository.updateActive(eq(USER_ID), eq(false), any(Instant.class))).thenReturn(1);

        userService.deactivate(USER_ID);

        verify(userRepository).updateActive(eq(USER_ID), eq(false), any(Instant.class));
    }

    @Test
    void deactivate_whenUserMissing_throwsResourceNotFoundException() {
        when(userRepository.updateActive(eq(USER_ID), eq(false), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> userService.deactivate(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_whenUserExists_deletesUser() {
        User user = user(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.delete(USER_ID);

        verify(userRepository).delete(user);
    }

    @Test
    void delete_whenUserMissing_throwsResourceNotFoundException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).delete(any(User.class));
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setName("Ivan");
        user.setSurname("Petrov");
        user.setBirthDate(LocalDate.of(1995, 5, 20));
        user.setEmail(EMAIL);
        return user;
    }

    private static UserDto userDto(Long id) {
        return new UserDto(id, "Ivan", "Petrov", LocalDate.of(1995, 5, 20), EMAIL, true, null, null);
    }

    private static PaymentCardDto cardDto(Long id) {
        return new PaymentCardDto(id, USER_ID, "4111111111111111", "IVAN PETROV",
                LocalDate.now().plusYears(3), true, null, null);
    }
}
