package by.darkimpulsepoint.userservice.dto;

import java.io.Serializable;
import java.util.List;

public record UserWithCardsDto(UserDto user, List<PaymentCardDto> cards) implements Serializable {
}
