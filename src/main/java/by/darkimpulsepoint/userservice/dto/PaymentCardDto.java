package by.darkimpulsepoint.userservice.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public record PaymentCardDto(
        Long id,
        Long userId,

        @NotBlank(message = "number must not be blank")
        @Pattern(regexp = "\\d{13,19}", message = "number must contain 13 to 19 digits")
        String number,

        @NotBlank(message = "holder must not be blank")
        @Size(max = 255, message = "holder must be at most 255 characters")
        String holder,

        @NotNull(message = "expirationDate must not be null")
        @Future(message = "expirationDate must be in the future")
        LocalDate expirationDate,

        Boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
