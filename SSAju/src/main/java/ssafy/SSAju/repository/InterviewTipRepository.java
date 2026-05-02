package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.InterviewTip;

import java.util.List;

public interface InterviewTipRepository extends JpaRepository<InterviewTip, Long> {

    List<InterviewTip> findByCareerConsultation(CareerConsultation careerConsultation);
}
