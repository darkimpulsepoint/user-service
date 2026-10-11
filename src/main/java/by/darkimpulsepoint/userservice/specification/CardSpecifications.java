package by.darkimpulsepoint.userservice.specification;

import by.darkimpulsepoint.userservice.entity.PaymentCard;
import by.darkimpulsepoint.userservice.entity.User;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class CardSpecifications {

    private CardSpecifications() {
    }

    public static Specification<PaymentCard> byOwnerNameAndSurname(String name, String surname) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(name) && !StringUtils.hasText(surname)) {
                return cb.conjunction();
            }
            Join<PaymentCard, User> owner = root.join("user");
            return cb.and(
                    UserSpecifications.contains(cb, owner.get("name"), name),
                    UserSpecifications.contains(cb, owner.get("surname"), surname));
        };
    }
}