package by.darkimpulsepoint.userservice.controller;

import by.darkimpulsepoint.userservice.dto.PageResponse;
import by.darkimpulsepoint.userservice.dto.PaymentCardDto;
import by.darkimpulsepoint.userservice.exception.InvalidRequestException;
import by.darkimpulsepoint.userservice.service.PaymentCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentCardController {

    private final PaymentCardService cardService;

    @PostMapping("/users/{userId}/cards")
    public ResponseEntity<PaymentCardDto> create(@PathVariable("userId") Long userId,
                                                 @Valid @RequestBody PaymentCardDto dto) {
        if (dto.userId() != null && !dto.userId().equals(userId)) {
            throw new InvalidRequestException(
                    "User id in body (" + dto.userId() + ") does not match path userId (" + userId + ")");
        }
        PaymentCardDto created = cardService.create(userId, dto);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/cards/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/users/{userId}/cards")
    public ResponseEntity<List<PaymentCardDto>> getAllByUserId(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(cardService.getAllByUserId(userId));
    }

    @GetMapping("/cards/{id}")
    public ResponseEntity<PaymentCardDto> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(cardService.getById(id));
    }

    @GetMapping("/cards")
    public ResponseEntity<PageResponse<PaymentCardDto>> getAll(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "surname", required = false) String surname,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(cardService.getAll(name, surname, pageable)));
    }

    @PutMapping("/cards/{id}")
    public ResponseEntity<PaymentCardDto> update(@PathVariable("id") Long id,
                                                 @Valid @RequestBody PaymentCardDto dto) {
        if (dto.id() != null && !dto.id().equals(id)) {
            throw new InvalidRequestException("Card id in body (" + dto.id() + ") does not match path id (" + id + ")");
        }
        return ResponseEntity.ok(cardService.update(id, dto));
    }

    @PatchMapping("/cards/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable("id") Long id) {
        cardService.activate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/cards/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable("id") Long id) {
        cardService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cards/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        cardService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
