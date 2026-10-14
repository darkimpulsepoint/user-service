package by.darkimpulsepoint.userservice.entity;

import by.darkimpulsepoint.userservice.exception.CardLimitExceededException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends AbstractEntity {

    public static final int MAX_CARDS = 5;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String surname;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentCard> cards = new ArrayList<>();

    public void addCard(PaymentCard card) {
        if (cards.size() >= MAX_CARDS) {
            throw new CardLimitExceededException("User cannot have more than " + MAX_CARDS + " cards");
        }
        cards.add(card);
        card.setUser(this);
    }

    public void removeCard(PaymentCard card) {
        cards.remove(card);
        card.setUser(null);
    }
}