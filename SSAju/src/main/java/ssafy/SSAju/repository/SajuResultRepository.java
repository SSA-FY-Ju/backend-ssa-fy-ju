package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;

import java.util.Optional;

public interface SajuResultRepository extends JpaRepository<SajuResult, Long> {

    Optional<SajuResult> findByUserProfile(UserProfile userProfile);

    /**
     * 마이페이지 상세 조회 전용: UserProfile과 CareerFortune을 fetch join하면서
     * 소유권(UserSajuAccess EXISTS 서브쿼리)까지 한 번에 확인한다.
     * CareerConsultationRepository.findByIdAndUserIdWithSajuResultAndProfile과 동일한 패턴(B1).
     */
    @Query("SELECT s FROM SajuResult s " +
           "LEFT JOIN FETCH s.userProfile " +
           "LEFT JOIN FETCH s.careerFortune " +
           "WHERE s.id = :id AND EXISTS (" +
           "  SELECT 1 FROM UserSajuAccess usa WHERE usa.sajuResult = s AND usa.user.id = :userId)")
    Optional<SajuResult> findByIdAndUserIdWithProfileAndFortune(@Param("id") Long id, @Param("userId") Long userId);
}
