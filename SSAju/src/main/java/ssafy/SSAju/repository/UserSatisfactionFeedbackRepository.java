package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserSatisfactionFeedbackRepository extends JpaRepository<UserSatisfactionFeedback, Long> {

    void deleteAllByUser(User user);

    List<UserSatisfactionFeedback> findAllByUser(User user);

    Optional<UserSatisfactionFeedback> findBySajuResult_IdAndUser(Long sajuResultId, User user);

    /** Mi-12: User 엔티티 전달 불필요 — userId만으로 조회 */
    Optional<UserSatisfactionFeedback> findBySajuResult_IdAndUser_Id(Long sajuResultId, Long userId);
}
