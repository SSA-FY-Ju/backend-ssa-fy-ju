package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;

public interface UserSatisfactionFeedbackRepository extends JpaRepository<UserSatisfactionFeedback, Long> {
}
