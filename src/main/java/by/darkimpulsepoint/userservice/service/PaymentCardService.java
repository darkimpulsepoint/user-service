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
import by.darkimpulsepoint.userservice.specification.CardSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static by.darkimpulsepoint.userservice.config.CacheConfig.USER_WITH_CARDS;

@Service
@RequiredArgsConstructor
public class PaymentCardService {

    private final PaymentCardRepository cardRepository;
    private final UserRepository userRepository;
    private final PaymentCardMapper cardMapper;
    private final CacheManager cacheManager;

    @Transactional
    public PaymentCardDto create(Long userId, PaymentCardDto dto) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));

        if (cardRepository.countCardsByUserId(userId) >= User.MAX_CARDS) {
            throw new CardLimitExceededException("User cannot have more than " + User.MAX_CARDS + " cards");
        }
        if (cardRepository.existsByNumber(dto.number())) {
            throw new DuplicateEntityException("Card with number " + dto.number() + " already exists");
        }

        PaymentCard card = cardMapper.toEntity(dto);
        card.setUser(user);
        PaymentCardDto saved = cardMapper.toDto(cardRepository.save(card));
        evictUserCache(userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public PaymentCardDto getById(Long id) {
        return cardMapper.toDto(findCard(id));
    }

    @Transactional(readOnly = true)
    public Page<PaymentCardDto> getAll(String name, String surname, Pageable pageable) {
        return cardRepository
                .findAll(CardSpecifications.byOwnerNameAndSurname(name, surname), pageable)
                .map(cardMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<PaymentCardDto> getAllByUserId(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User with id " + userId + " not found");
        }
        return cardRepository.findCardsByUserId(userId).stream()
                .map(cardMapper::toDto)
                .toList();
    }

    @Transactional
    public PaymentCardDto update(Long id, PaymentCardDto dto) {
        PaymentCard card = findCard(id);
        if (cardRepository.existsByNumberAndIdNot(dto.number(), id)) {
            throw new DuplicateEntityException("Card with number " + dto.number() + " already exists");
        }
        cardMapper.updateEntity(dto, card);
        PaymentCardDto updated = cardMapper.toDto(cardRepository.saveAndFlush(card));
        evictUserCache(updated.userId());
        return updated;
    }

    @Transactional
    public void activate(Long id) {
        setActive(id, true);
    }

    @Transactional
    public void deactivate(Long id) {
        setActive(id, false);
    }

    @Transactional
    public void delete(Long id) {
        PaymentCard card = findCard(id);
        Long userId = card.getUser().getId();
        cardRepository.delete(card);
        evictUserCache(userId);
    }

    private void setActive(Long id, boolean active) {
        Long userId = findCard(id).getUser().getId();
        if (cardRepository.updateActive(id, active, Instant.now()) == 0) {
            throw notFound(id);
        }
        evictUserCache(userId);
    }

    private void evictUserCache(Long userId) {
        Cache cache = cacheManager.getCache(USER_WITH_CARDS);
        if (cache != null) {
            cache.evict(userId);
        }
    }

    private PaymentCard findCard(Long id) {
        return cardRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Card with id " + id + " not found");
    }
}
