package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;

import java.util.Optional;

public interface SajuResultRepository extends JpaRepository<SajuResult, Long> {

    Optional<SajuResult> findByUserProfile(UserProfile userProfile);

    Optional<SajuResult> findByIdAndUser_Id(Long id, Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM SajuResult s WHERE s.userProfile = :userProfile")
    void deleteByUserProfileJpql(@Param("userProfile") UserProfile userProfile);
}
