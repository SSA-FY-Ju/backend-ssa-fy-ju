package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.entity.User;

import java.util.Optional;

public interface SajuResultRepository extends JpaRepository<SajuResult, Long> {

    Optional<SajuResult> findByUserAndUserProfile(User user, UserProfile userProfile);

    Optional<SajuResult> findByIdAndUser_Id(Long id, Long userId);

    /**
     * 마이페이지 상세 조회 전용: UserProfile과 CareerFortune을 한 번의 쿼리로 fetch join.
     * UserService.buildSajuDetail()의 레이지 로딩 체인(3개 SELECT)을 1개 쿼리로 개선.
     */
    @Query("SELECT s FROM SajuResult s " +
           "LEFT JOIN FETCH s.userProfile " +
           "LEFT JOIN FETCH s.careerFortune " +
           "WHERE s.id = :id AND s.user.id = :userId")
    Optional<SajuResult> findByIdAndUser_IdWithProfileAndFortune(@Param("id") Long id,
                                                                   @Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM SajuResult s WHERE s.user = :user AND s.userProfile = :userProfile")
    void deleteByUserAndUserProfileJpql(@Param("user") User user, @Param("userProfile") UserProfile userProfile);
}
