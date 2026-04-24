package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.UserProfile;

import java.time.LocalDate;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByBirthDate(LocalDate birthDate);
}
