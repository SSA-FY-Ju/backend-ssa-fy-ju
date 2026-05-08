package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.util.JobCategoryEnum;

import java.util.Optional;

public interface CompanyCompatibilityRepository extends JpaRepository<CompanyCompatibility, Long> {

    Optional<CompanyCompatibility> findByUserProfile_IdAndCompanyNameAndTargetRoleCategory(
            Long userProfileId, String companyName, JobCategoryEnum targetRoleCategory);
}
