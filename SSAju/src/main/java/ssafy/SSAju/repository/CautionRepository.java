package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.Caution;

import java.util.List;

public interface CautionRepository extends JpaRepository<Caution, Long> {

    List<Caution> findByCompanyCompatibility_Id(Long compatibilityId);
}
