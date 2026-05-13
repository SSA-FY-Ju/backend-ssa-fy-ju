package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.ActionableKeyword;

import java.util.List;

public interface ActionableKeywordRepository extends JpaRepository<ActionableKeyword, Long> {

    List<ActionableKeyword> findByActionableStrategy_IdOrderByDisplayOrderAsc(Long actionableStrategyId);
}
