package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.FiveElementsAnalysis;

import java.util.Optional;

public interface FiveElementsAnalysisRepository extends JpaRepository<FiveElementsAnalysis, Long> {

    Optional<FiveElementsAnalysis> findByCompanyCompatibility_Id(Long compatibilityId);
}
