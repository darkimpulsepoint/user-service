package by.darkimpulsepoint.userservice.service;

import by.darkimpulsepoint.userservice.dto.PaymentCardDto;
import by.darkimpulsepoint.userservice.entity.PaymentCard;
import by.darkimpulsepoint.userservice.entity.User;
import by.darkimpulsepoint.userservice.exception.CardLimitExceededException;
import by.darkimpulsepoint.userservice.exception.DuplicateEntityException;
import by.darkimpulsepoint.userservice.exception.ResourceNotFoundException;
import by.darkimpulsepoint.userservice.mapper.PaymentCardMapper;
import by.darkimpulsepoint.userservice.repository.PaymentCardRepository;
import by.darkimpulsepoint.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static by.darkimpulsepoint.userservice.config.CacheConfig.USER_WITH_CARDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCardServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CARD_ID = 5L;
    private static final String NUMBER = "4111111111111111";

    @Mock
    private PaymentCardRepository cardRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentCardMapper cardMapper;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;

    @InjectMocks
    private PaymentCardService cardService;

    @Test
    void create_whenAllChecksPass_savesCardAndEvictsUserCache() {
        User user = user();
        PaymentCard card = card(null, user);
        PaymentCardDto request = cardDto(null);
        PaymentCardDto response = cardDto(CARD_ID);

        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(cardRepository.countCardsByUserId(USER_ID)).thenReturn(2L);
        when(cardRepository.existsByNumber(NUMBER)).thenReturn(false);
        when(cardMapper.toEntity(request)).thenReturn(card);
        when(cardRepository.save(card)).thenReturn(card);
        when(cardMapper.toDto(card)).thenReturn(response);
        when(cacheManager.getCache(USER_WITH_CARDS)).thenReturn(cache);

        PaymentCardDto result = cardService.create(USER_ID, request);

        assertThat(result).isEqualTo(response);
        assertThat(card.getUser()).isSameAs(user);
        verify(cache).evict(USER_ID);
    }

    @Test
    void create_whenUserMissing_throwsResourceNotFoundException() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.create(USER_ID, cardDto(null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(USER_ID));

        verify(cardRepository, never()).save(any());
        verifyNoInteractions(cacheManager);
    }

    @Test
    void create_whenUserAlreadyHasMaxCards_throwsCardLimitExceededException() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user()));
        when(cardRepository.countCardsByUserId(USER_ID)).thenReturn((long) User.MAX_CARDS);

        assertThatThrownBy(() -> cardService.create(USER_ID, cardDto(null)))
                .isInstanceOf(CardLimitExceededException.class);

        verify(cardRepository, never()).save(any());
        verifyNoInteractions(cacheManager);
    }

    @Test
    void create_whenCardNumberExists_throwsDuplicateEntityException() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user()));
        when(cardRepository.countCardsByUserId(USER_ID)).thenReturn(0L);
        when(cardRepository.existsByNumber(NUMBER)).thenReturn(true);

        assertThatThrownBy(() -> cardService.create(USER_ID, cardDto(null)))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessageContaining(NUMBER);

        verify(cardRepository, never()).save(any());
        verifyNoInteractions(cacheManager);
    }

    @Test
    void getById_whenCardExists_returnsDto() {
        PaymentCard card = card(CARD_ID, user());
        PaymentCardDto dto = cardDto(CARD_ID);
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
        when(cardMapper.toDto(card)).thenReturn(dto);

        assertThat(cardService.getById(CARD_ID)).isEqualTo(dto);
    }

    @Test
    void getById_whenCardMissing_throwsResourceNotFoundException() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getById(CARD_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(CARD_ID));
    }

    @Test
    void getAll_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 10);
        PaymentCard card = card(CARD_ID, user());
        PaymentCardDto dto = cardDto(CARD_ID);
        Page<PaymentCard> page = new PageImpl<>(List.of(card), pageable, 1);

        when(cardRepository.findAll(ArgumentMatchers.<Specification<PaymentCard>>any(), eq(pageable)))
                .thenReturn(page);
        when(cardMapper.toDto(card)).thenReturn(dto);

        Page<PaymentCardDto> result = cardService.getAll("Ivan", "Petrov", pageable);

        assertThat(result.getContent()).containsExactly(dto);
    }

    @Test
    void getAllByUserId_whenUserExists_returnsMappedCards() {
        PaymentCard first = card(CARD_ID, user());
        PaymentCard second = card(6L, user());
        PaymentCardDto firstDto = cardDto(CARD_ID);
        PaymentCardDto secondDto = cardDto(6L);

        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(cardRepository.findCardsByUserId(USER_ID)).thenReturn(List.of(first, second));
        when(cardMapper.toDto(first)).thenReturn(firstDto);
        when(cardMapper.toDto(second)).thenReturn(secondDto);

        assertThat(cardService.getAllByUserId(USER_ID)).containsExactly(firstDto, secondDto);
    }

    @Test
    void getAllByUserId_whenUserMissing_throwsResourceNotFoundException() {
        when(userRepository.existsById(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> cardService.getAllByUserId(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(cardRepository, never()).findCardsByUserId(any());
    }

    @Test
    void update_whenCardExistsAndNumberFree_updatesAndEvictsUserCache() {
        PaymentCard card = card(CARD_ID, user());
        PaymentCardDto request = cardDto(null);
        PaymentCardDto response = cardDto(CARD_ID);

        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
        when(cardRepository.existsByNumberAndIdNot(NUMBER, CARD_ID)).thenReturn(false);
        when(cardRepository.saveAndFlush(card)).thenReturn(card);
        when(cardMapper.toDto(card)).thenReturn(response);
        when(cacheManager.getCache(USER_WITH_CARDS)).thenReturn(cache);

        PaymentCardDto result = cardService.update(CARD_ID, request);

        assertThat(result).isEqualTo(response);
        verify(cardMapper).updateEntity(request, card);
        verify(cache).evict(USER_ID);
    }

    @Test
    void update_whenCardMissing_throwsResourceNotFoundException() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.update(CARD_ID, cardDto(null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(cardRepository, never()).saveAndFlush(any());
        verifyNoInteractions(cacheManager);
    }

    @Test
    void update_whenNumberBelongsToAnotherCard_throwsDuplicateEntityException() {
        PaymentCard card = card(CARD_ID, user());
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
        when(cardRepository.existsByNumberAndIdNot(NUMBER, CARD_ID)).thenReturn(true);

        assertThatThrownBy(() -> cardService.update(CARD_ID, cardDto(null)))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessageContaining(NUMBER);

        verify(cardMapper, never()).updateEntity(any(), any());
        verify(cardRepository, never()).saveAndFlush(any());
        verifyNoInteractions(cacheManager);
    }

    @Test
    void activate_whenCardExists_setsActiveTrueAndEvictsUserCache() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card(CARD_ID, user())));
        when(cardRepository.updateActive(eq(CARD_ID), eq(true), any(Instant.class))).thenReturn(1);
        when(cacheManager.getCache(USER_WITH_CARDS)).thenReturn(cache);

        cardService.activate(CARD_ID);

        verify(cardRepository).updateActive(eq(CARD_ID), eq(true), any(Instant.class));
        verify(cache).evict(USER_ID);
    }

    @Test
    void activate_whenCardMissing_throwsResourceNotFoundException() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.activate(CARD_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(cardRepository, never()).updateActive(any(), ArgumentMatchers.anyBoolean(), any());
        verifyNoInteractions(cacheManager);
    }

    @Test
    void activate_whenCardRemovedBeforeUpdate_throwsAndKeepsCache() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card(CARD_ID, user())));
        when(cardRepository.updateActive(eq(CARD_ID), eq(true), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> cardService.activate(CARD_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(cacheManager);
    }

    @Test
    void deactivate_whenCardExists_setsActiveFalseAndEvictsUserCache() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card(CARD_ID, user())));
        when(cardRepository.updateActive(eq(CARD_ID), eq(false), any(Instant.class))).thenReturn(1);
        when(cacheManager.getCache(USER_WITH_CARDS)).thenReturn(cache);

        cardService.deactivate(CARD_ID);

        verify(cardRepository).updateActive(eq(CARD_ID), eq(false), any(Instant.class));
        verify(cache).evict(USER_ID);
    }

    @Test
    void deactivate_whenCardMissing_throwsResourceNotFoundException() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.deactivate(CARD_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(cacheManager);
    }

    @Test
    void delete_whenCardExists_deletesCardAndEvictsUserCache() {
        PaymentCard card = card(CARD_ID, user());
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
        when(cacheManager.getCache(USER_WITH_CARDS)).thenReturn(cache);

        cardService.delete(CARD_ID);

        verify(cardRepository).delete(card);
        verify(cache).evict(USER_ID);
    }

    @Test
    void delete_whenCardMissing_throwsResourceNotFoundException() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.delete(CARD_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(cardRepository, never()).delete(any(PaymentCard.class));
        verifyNoInteractions(cacheManager);
    }

    private static User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setName("Ivan");
        user.setSurname("Petrov");
        user.setBirthDate(LocalDate.of(1995, 5, 20));
        user.setEmail("ivan@mail.com");
        return user;
    }

    private static PaymentCard card(Long id, User owner) {
        PaymentCard card = new PaymentCard();
        card.setId(id);
        card.setUser(owner);
        card.setNumber(NUMBER);
        card.setHolder("IVAN PETROV");
        card.setExpirationDate(LocalDate.now().plusYears(3));
        return card;
    }

    private static PaymentCardDto cardDto(Long id) {
        return new PaymentCardDto(id, USER_ID, NUMBER, "IVAN PETROV",
                LocalDate.now().plusYears(3), true, null, null);
    }
}
