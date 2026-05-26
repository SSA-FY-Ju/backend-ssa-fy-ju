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

    Optional<CompanyCompatibility> findByIdAndUser(Long id, User user);

    List<CompanyCompatibility> findByUser_IdOrderByAnalyzedAtDesc(Long userId);

    /**
     * 이번 달 분석 캐시 조회 (월별 캐시 패턴 핵심 조회).
     * UNIQUE(user_id, user_profile_id, company_name, target_role_category, compatibility_month) 제약상
     * 결과는 최대 1개입니다.
     */
    Optional<CompanyCompatibility> findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
            Long userId, Long userProfileId, String companyName,
            JobCategoryEnum targetRoleCategory, Integer compatibilityMonth);

    /**
     * 자식 엔티티 저장이 모두 완료된 후 completed 플래그를 true로 업데이트합니다.
     * CompatibilityChildSaveService에서만 호출해야 합니다.
     */
    @Modifying
    @Query("UPDATE CompanyCompatibility c SET c.completed = true WHERE c.id = :id")
    void markCompleted(@Param("id") Long id);
}
