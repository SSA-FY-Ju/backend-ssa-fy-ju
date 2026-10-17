package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.CareerFortune;

public interface CareerFortuneRepository extends JpaRepository<CareerFortune, Long> {
}
