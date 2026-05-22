package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
