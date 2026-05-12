package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.RoleCompatibility;

import java.util.List;

public interface RoleCompatibilityRepository extends JpaRepository<RoleCompatibility, Long> {

    List<RoleCompatibility> findByCompanyCompatibility_Id(Long compatibilityId);
}
