package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.MonthlyForecast;

import java.util.List;

public interface MonthlyForecastRepository extends JpaRepository<MonthlyForecast, Long> {

    List<MonthlyForecast> findByCompanyCompatibility_Id(Long compatibilityId);
}
