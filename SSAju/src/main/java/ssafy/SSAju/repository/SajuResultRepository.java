package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;

import java.util.Optional;

public interface SajuResultRepository extends JpaRepository<SajuResult, Long> {

    Optional<SajuResult> findByUserProfile(UserProfile userProfile);
}
