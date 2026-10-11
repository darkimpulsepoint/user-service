package by.darkimpulsepoint.userservice.repository;

import by.darkimpulsepoint.userservice.entity.PaymentCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PaymentCardRepository
        extends JpaRepository<PaymentCard, Long>, JpaSpecificationExecutor<PaymentCard> {

    boolean existsByNumber(String number);

    boolean existsByNumberAndIdNot(String number, Long id);

    @Query("select c from PaymentCard c where c.user.id = :userId order by c.id")
    List<PaymentCard> findCardsByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT COUNT(*) FROM payment_cards WHERE user_id = :userId", nativeQuery = true)
    long countCardsByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query("update PaymentCard c set c.active = :active, c.updatedAt = :now where c.id = :id")
    int updateActive(@Param("id") Long id, @Param("active") boolean active, @Param("now") Instant now);
}