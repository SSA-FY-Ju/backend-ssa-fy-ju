package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.SajuResult;

import java.util.List;
import java.util.Optional;

public interface CareerConsultationRepository extends JpaRepository<CareerConsultation, Long> {

    List<CareerConsultation> findBySajuResult(SajuResult sajuResult);

    /**
     * M-9: 같은 달 캐시 히트 조회. consultationMonth 포맷은 "yyyy-MM" (예: "2026-05").
     */
    Optional<CareerConsultation> findBySajuResultAndConsultationMonth(SajuResult sajuResult,
                                                                       String consultationMonth);

    /**
     * M-5: SajuResult 교체 시 연관된 CareerConsultation 전체 삭제 (FK 제약 위반 방지).
     */
    @Modifying
    @Query("DELETE FROM CareerConsultation cc WHERE cc.sajuResult.id = :sajuResultId")
    void deleteBySajuResultId(@Param("sajuResultId") Long sajuResultId);
}
