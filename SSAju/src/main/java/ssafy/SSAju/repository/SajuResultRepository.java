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
     * 마이페이지 상세 조회 전용: UserProfile과 CareerFortune을 한 번의 쿼리로 fetch join.
     * UserService.buildSajuDetail()의 레이지 로딩 체인(3개 SELECT)을 1개 쿼리로 개선.
     * 소유권(UserSajuAccess) 확인은 호출자가 별도로 수행한다(B1).
     */
    @Query("SELECT s FROM SajuResult s " +
           "LEFT JOIN FETCH s.userProfile " +
           "LEFT JOIN FETCH s.careerFortune " +
           "WHERE s.id = :id")
    Optional<SajuResult> findByIdWithProfileAndFortune(@Param("id") Long id);
}
