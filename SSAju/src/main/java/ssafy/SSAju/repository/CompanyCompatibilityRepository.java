package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.entity.User;

import java.util.List;
import java.util.Optional;

public interface CompanyCompatibilityRepository extends JpaRepository<CompanyCompatibility, Long> {

    Optional<CompanyCompatibility> findByIdAndUser(Long id, User user);

    List<CompanyCompatibility> findByUser_IdOrderByAnalyzedAtDesc(Long userId);

    /**
     * "완료된" 이번 달 분석 캐시만 조회 — {@code CompanyMatchingService}의 락 없는 1차 조회와
     * {@code CompanyCompatibilitySaveService}의 락 안 재확인이 이 메서드 하나를 공유해,
     * 두 곳이 서로 다른 조건으로 갈라지는 것을 막는다.
     */
    Optional<CompanyCompatibility> findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonthAndCompletedTrue(
            Long userId, Long userProfileId, String companyName,
            JobCategoryEnum targetRoleCategory, Integer compatibilityMonth);
}
