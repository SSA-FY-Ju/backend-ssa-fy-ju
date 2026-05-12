package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.ActionableStrategy;

import java.util.Optional;

public interface ActionableStrategyRepository extends JpaRepository<ActionableStrategy, Long> {

    Optional<ActionableStrategy> findByCompanyCompatibility_Id(Long compatibilityId);
}
