package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.entity.User;

public interface UserSatisfactionFeedbackRepository extends JpaRepository<UserSatisfactionFeedback, Long> {

    /** 회원 탈퇴 시 해당 사용자의 피드백을 hard delete한다. */
    void deleteAllByUser(User user);

    /** 분석당 피드백 0..1개 — 이미 작성한 분석인지 확인한다. */
    boolean existsBySajuId(Long sajuId);
}
