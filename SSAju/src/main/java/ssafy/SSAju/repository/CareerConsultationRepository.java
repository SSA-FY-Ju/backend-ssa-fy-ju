package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.SajuResult;

import java.util.List;

public interface CareerConsultationRepository extends JpaRepository<CareerConsultation, Long> {

    List<CareerConsultation> findBySajuResult(SajuResult sajuResult);
}
