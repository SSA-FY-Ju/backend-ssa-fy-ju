package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.entity.User;

public interface UserSatisfactionFeedbackRepository extends JpaRepository<UserSatisfactionFeedback, Long> {

    void deleteAllByUser(User user);
}
