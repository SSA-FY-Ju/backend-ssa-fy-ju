package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.TargetRoleAnalysis;

import java.util.Optional;

public interface TargetRoleAnalysisRepository extends JpaRepository<TargetRoleAnalysis, Long> {

    Optional<TargetRoleAnalysis> findByCompanyCompatibility_Id(Long compatibilityId);
}
