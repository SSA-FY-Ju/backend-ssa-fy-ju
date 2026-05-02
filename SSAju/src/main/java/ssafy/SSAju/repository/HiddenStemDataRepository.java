package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.HiddenStemData;
import ssafy.SSAju.career.entity.SajuResult;

import java.util.List;

public interface HiddenStemDataRepository extends JpaRepository<HiddenStemData, Long> {

    List<HiddenStemData> findBySajuResult(SajuResult sajuResult);

    @Modifying
    @Transactional
    @Query("DELETE FROM HiddenStemData h WHERE h.sajuResult.id = :sajuResultId")
    void deleteBySajuResultId(@Param("sajuResultId") Long sajuResultId);
}
