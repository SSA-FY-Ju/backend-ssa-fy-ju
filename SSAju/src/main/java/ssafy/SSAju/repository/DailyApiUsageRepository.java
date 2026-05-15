package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.entity.DailyApiUsage;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailyApiUsageRepository extends JpaRepository<DailyApiUsage, Long> {

    Optional<DailyApiUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);
}
