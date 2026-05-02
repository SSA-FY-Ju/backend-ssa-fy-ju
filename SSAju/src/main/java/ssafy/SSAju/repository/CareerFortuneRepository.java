package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.CareerFortune;
import ssafy.SSAju.career.entity.SajuResult;

import java.util.Optional;

public interface CareerFortuneRepository extends JpaRepository<CareerFortune, Long> {

    Optional<CareerFortune> findBySajuResult(SajuResult sajuResult);

    @Modifying
    @Transactional
    @Query("DELETE FROM CareerFortune c WHERE c.sajuResult.id = :sajuResultId")
    void deleteBySajuResultId(@Param("sajuResultId") Long sajuResultId);
}
