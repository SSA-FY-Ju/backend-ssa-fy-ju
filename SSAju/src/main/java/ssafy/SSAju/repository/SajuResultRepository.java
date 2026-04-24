package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.SSAju.career.entity.SajuResult;

import java.util.Optional;

public interface SajuResultRepository extends JpaRepository<SajuResult, Long> {

    @Query("SELECT s FROM SajuResult s WHERE s.userProfile.id = :userProfileId ORDER BY s.fetchedAt DESC LIMIT 1")
    Optional<SajuResult> findLatestByUserProfileId(@Param("userProfileId") Long userProfileId);
}
