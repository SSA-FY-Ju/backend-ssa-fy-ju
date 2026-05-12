package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.LuckyDay;

import java.util.List;

public interface LuckyDayRepository extends JpaRepository<LuckyDay, Long> {

    List<LuckyDay> findByActionableStrategy_IdOrderByDisplayOrderAsc(Long actionableStrategyId);
}
