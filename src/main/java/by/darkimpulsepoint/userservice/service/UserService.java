package by.darkimpulsepoint.userservice.service;

import by.darkimpulsepoint.userservice.dto.PaymentCardDto;
import by.darkimpulsepoint.userservice.dto.UserDto;
import by.darkimpulsepoint.userservice.dto.UserWithCardsDto;
import by.darkimpulsepoint.userservice.entity.User;
import by.darkimpulsepoint.userservice.exception.DuplicateEntityException;
import by.darkimpulsepoint.userservice.exception.ResourceNotFoundException;
import by.darkimpulsepoint.userservice.mapper.PaymentCardMapper;
import by.darkimpulsepoint.userservice.mapper.UserMapper;
import by.darkimpulsepoint.userservice.repository.PaymentCardRepository;
import by.darkimpulsepoint.userservice.repository.UserRepository;
import by.darkimpulsepoint.userservice.specification.UserSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static by.darkimpulsepoint.userservice.config.CacheConfig.USER_WITH_CARDS;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PaymentCardRepository cardRepository;
    private final PaymentCardMapper cardMapper;

    @Transactional
    public UserDto create(UserDto dto) {
        if (userRepository.existsByEmail(dto.email())) {
            throw new DuplicateEntityException("User with email " + dto.email() + " already exists");
        }
        User saved = userRepository.save(userMapper.toEntity(dto));
        return userMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public UserDto getById(Long id) {
        return userMapper.toDto(findUser(id));
    }

    @Cacheable(cacheNames = USER_WITH_CARDS, key = "#id")
    @Transactional(readOnly = true)
    public UserWithCardsDto getByIdWithCards(Long id) {
        UserDto user = userMapper.toDto(findUser(id));
        List<PaymentCardDto> cards = cardRepository.findCardsByUserId(id).stream()
                .map(cardMapper::toDto)
                .toList();
        return new UserWithCardsDto(user, cards);
    }

    @Transactional(readOnly = true)
    public Page<UserDto> getAll(String name, String surname, Pageable pageable) {
        return userRepository
                .findAll(UserSpecifications.byNameAndSurname(name, surname), pageable)
                .map(userMapper::toDto);
    }

    @CacheEvict(cacheNames = USER_WITH_CARDS, key = "#id")
    @Transactional
    public UserDto update(Long id, UserDto dto) {
        User user = findUser(id);
        if (userRepository.existsByEmailAndIdNot(dto.email(), id)) {
            throw new DuplicateEntityException("User with email " + dto.email() + " already exists");
        }
        userMapper.updateEntity(dto, user);
        return userMapper.toDto(userRepository.saveAndFlush(user));
    }

    @CacheEvict(cacheNames = USER_WITH_CARDS, key = "#id")
    @Transactional
    public void activate(Long id) {
        setActive(id, true);
    }

    @CacheEvict(cacheNames = USER_WITH_CARDS, key = "#id")
    @Transactional
    public void deactivate(Long id) {
        setActive(id, false);
    }

    @CacheEvict(cacheNames = USER_WITH_CARDS, key = "#id")
    @Transactional
    public void delete(Long id) {
        userRepository.delete(findUser(id));
    }

    private void setActive(Long id, boolean active) {
        if (userRepository.updateActive(id, active, Instant.now()) == 0) {
            throw notFound(id);
        }
    }

    private User findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("User with id " + id + " not found");
    }
}
