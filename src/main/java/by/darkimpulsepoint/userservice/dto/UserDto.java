package by.darkimpulsepoint.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public record UserDto(
        Long id,

        @NotBlank(message = "name must not be blank")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @NotBlank(message = "surname must not be blank")
        @Size(max = 100, message = "surname must be at most 100 characters")
        String surname,

        @NotNull(message = "birthDate must not be null")
        @Past(message = "birthDate must be in the past")
        LocalDate birthDate,

        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
