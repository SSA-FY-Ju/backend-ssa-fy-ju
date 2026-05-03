package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.Strength;

import java.util.List;

public interface StrengthRepository extends JpaRepository<Strength, Long> {

    List<Strength> findByCareerConsultation(CareerConsultation careerConsultation);
}
