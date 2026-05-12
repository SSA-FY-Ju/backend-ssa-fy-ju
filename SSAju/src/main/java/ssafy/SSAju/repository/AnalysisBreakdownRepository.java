package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.AnalysisBreakdown;

import java.util.Optional;

public interface AnalysisBreakdownRepository extends JpaRepository<AnalysisBreakdown, Long> {

    Optional<AnalysisBreakdown> findByCompanyCompatibility_Id(Long compatibilityId);
}
