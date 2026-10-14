package by.darkimpulsepoint.userservice.repository;

import by.darkimpulsepoint.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update User u set u.active = :active, u.updatedAt = :now where u.id = :id")
    int updateActive(@Param("id") Long id, @Param("active") boolean active, @Param("now") Instant now);
}