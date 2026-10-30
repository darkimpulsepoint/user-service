package by.darkimpulsepoint.userservice.controller;

import by.darkimpulsepoint.userservice.dto.PageResponse;
import by.darkimpulsepoint.userservice.dto.UserDto;
import by.darkimpulsepoint.userservice.dto.UserWithCardsDto;
import by.darkimpulsepoint.userservice.exception.InvalidRequestException;
import by.darkimpulsepoint.userservice.security.SecurityUtils;
import by.darkimpulsepoint.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody UserDto dto) {
        UserDto created = userService.create(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrent() {
        return ResponseEntity.ok(userService.getById(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/me/with-cards")
    public ResponseEntity<UserWithCardsDto> getCurrentWithCards() {
        return ResponseEntity.ok(userService.getByIdWithCards(SecurityUtils.getCurrentUserId()));
    }

    @PreAuthorize("hasRole('ADMIN') or @access.isOwner(#id)")
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN') or @access.isOwner(#id)")
    @GetMapping("/{id}/with-cards")
    public ResponseEntity<UserWithCardsDto> getByIdWithCards(@PathVariable("id") Long id) {
        return ResponseEntity.ok(userService.getByIdWithCards(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<PageResponse<UserDto>> getAll(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "surname", required = false) String surname,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(userService.getAll(name, surname, pageable)));
    }

    @PreAuthorize("hasRole('ADMIN') or @access.isOwner(#id)")
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> update(@PathVariable("id") Long id, @Valid @RequestBody UserDto dto) {
        if (dto.id() != null && !dto.id().equals(id)) {
            throw new InvalidRequestException("User id in body (" + dto.id() + ") does not match path id (" + id + ")");
        }
        return ResponseEntity.ok(userService.update(id, dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable("id") Long id) {
        userService.activate(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable("id") Long id) {
        userService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
