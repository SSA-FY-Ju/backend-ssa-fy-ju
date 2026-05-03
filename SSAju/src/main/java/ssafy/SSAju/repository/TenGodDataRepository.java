package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.TenGodData;

import java.util.List;

public interface TenGodDataRepository extends JpaRepository<TenGodData, Long> {

    List<TenGodData> findBySajuResult(SajuResult sajuResult);

    @Modifying
    @Transactional
    @Query("DELETE FROM TenGodData t WHERE t.sajuResult.id = :sajuResultId")
    void deleteBySajuResultId(@Param("sajuResultId") Long sajuResultId);
}
