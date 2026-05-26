package ssafy.SSAju.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.entity.User;

import java.util.Optional;


/**
 * CompanyCompatibility 월별 캐시 패턴을 위한 JDBC 저장소.
 *
 * <p>CareerConsultation과 동일한 월별 캐시 패턴 적용:
 * UNIQUE(user_id, user_profile_id, company_name, target_role_category, compatibility_month) 제약으로
 * 동일 사용자가 같은 달에 동일 기업/직무를 중복 분석하는 것을 방지합니다.
 *
 * <p>INSERT IGNORE 패턴으로 race condition을 안전하게 처리합니다.
 * (SELECT FOR UPDATE + 버전 증가 방식을 제거하여 동시성 성능 개선)
 */
@Repository
@RequiredArgsConstructor
public class CompanyCompatibilityJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * UNIQUE(user_id, user_profile_id, company_name, target_role_category, compatibility_month) 제약으로
     * 동일 월 중복 삽입을 방지합니다.
     *
     * @param entity 저장할 CompanyCompatibility (compatibilityMonth 필드 포함)
     * @return 1 (신규 삽입 성공), 0 (이번 달 분석 결과가 이미 존재 — 캐시 히트)
     */
    public int insertOrIgnore(CompanyCompatibility entity) {
        return jdbcTemplate.update(
                "INSERT IGNORE INTO company_compatibility " +
                        "(user_profile_id, user_id, company_name, target_role_category, target_role_detail_name, " +
                        "completed, compatibility_score, summary, compatibility_month, analyzed_at, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                entity.getUserProfile().getId(),
                entity.getUser().getId(),
                entity.getCompanyName(),
                entity.getTargetRoleCategory().name(),
                entity.getTargetRoleDetailName(),
                false,
                entity.getCompatibilityScore(),
                entity.getSummary(),
                entity.getCompatibilityMonth(),
                entity.getAnalyzedAt()
        );
    }

    /**
     * 이번 달 분석 캐시를 조회합니다.
     *
     * <p>UNIQUE 제약 조건상 해당 조건에 맞는 레코드는 최대 1개만 존재합니다.
     *
     * @param user               사용자
     * @param userProfileId      사용자 프로필 ID
     * @param companyName        기업명
     * @param targetRoleCategory 직무 카테고리
     * @param compatibilityMonth 조회할 월 (YYYYMM 형식 정수, 예: 202605)
     * @return 이번 달 분석 결과 ID (없으면 empty)
     */
    public Optional<Long> findIdByUserAndCompanyAndRoleAndMonth(User user, Long userProfileId,
                                                                 String companyName,
                                                                 JobCategoryEnum targetRoleCategory,
                                                                 Integer compatibilityMonth) {
        return jdbcTemplate.query(
                "SELECT id FROM company_compatibility " +
                "WHERE user_id = ? AND user_profile_id = ? AND company_name = ? " +
                "AND target_role_category = ? AND compatibility_month = ? " +
                "LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                user.getId(), userProfileId, companyName, targetRoleCategory.name(), compatibilityMonth
        ).stream().findFirst();
    }
}
