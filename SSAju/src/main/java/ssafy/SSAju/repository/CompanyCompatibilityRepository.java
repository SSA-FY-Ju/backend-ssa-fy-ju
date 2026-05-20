package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.entity.User;

import java.util.List;
import java.util.Optional;

public interface CompanyCompatibilityRepository extends JpaRepository<CompanyCompatibility, Long> {

    Optional<CompanyCompatibility> findByUserProfile_IdAndCompanyNameAndTargetRoleCategory(
            Long userProfileId, String companyName, JobCategoryEnum targetRoleCategory);

    Optional<CompanyCompatibility> findFirstByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryOrderByVersionDesc(
            Long userId, Long userProfileId, String companyName, JobCategoryEnum targetRoleCategory);

    Optional<CompanyCompatibility> findByIdAndUser(Long id, User user);

    List<CompanyCompatibility> findByUser_IdOrderByAnalyzedAtDesc(Long userId);

    List<CompanyCompatibility> findByUser_IdAndCompanyNameOrderByVersionDesc(Long userId, String companyName);

    Optional<CompanyCompatibility> findFirstByUser_IdAndCompanyNameAndTargetRoleCategoryOrderByVersionDesc(
            Long userId, String companyName, JobCategoryEnum targetRoleCategory);

    /**
     * 자식 엔티티 저장이 모두 완료된 후 completed 플래그를 true로 업데이트합니다.
     * CompatibilityChildSaveService에서만 호출해야 합니다.
     */
    @Modifying
    @Query("UPDATE CompanyCompatibility c SET c.completed = true WHERE c.id = :id")
    void markCompleted(@Param("id") Long id);
}
