package by.darkimpulsepoint.userservice.specification;

import by.darkimpulsepoint.userservice.entity.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> byNameAndSurname(String name, String surname) {
        return (root, query, cb) -> cb.and(
                contains(cb, root.get("name"), name),
                contains(cb, root.get("surname"), surname));
    }

    static Predicate contains(CriteriaBuilder cb, Expression<String> field, String value) {
        if (!StringUtils.hasText(value)) {
            return cb.conjunction();
        }
        String escaped = value.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return cb.like(cb.lower(field), "%" + escaped + "%", '\\');
    }
}