package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.SajuFullData;
import ssafy.SSAju.career.entity.SajuResult;

import java.util.Optional;

public interface SajuFullDataRepository extends JpaRepository<SajuFullData, Long> {

    Optional<SajuFullData> findBySajuResult(SajuResult sajuResult);
}
