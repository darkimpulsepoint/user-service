package by.darkimpulsepoint.userservice.exception;

public class CardLimitExceededException extends RuntimeException {

    public CardLimitExceededException(String message) {
        super(message);
    }
}
