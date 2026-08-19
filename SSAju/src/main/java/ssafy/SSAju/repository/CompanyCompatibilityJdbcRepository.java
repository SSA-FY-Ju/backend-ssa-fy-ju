package ssafy.SSAju.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.DataAccessException;

import java.util.Optional;


/**
 * CompanyCompatibility 월별 캐시 패턴을 위한 JDBC 저장소.
 *
 * <p>동일 사용자가 같은 달에 동일 기업/직무를 중복 분석하는 것은
 * UNIQUE(user_id, user_profile_id, company_name, target_role_category, compatibility_month) 제약과
 * userProfile+company+role 단위 분산락({@code CompanyCompatibilityLockedAnalysisService}, US5)이
 * 함께 막습니다. 락이 동시 삽입 자체를 막으므로, 이 클래스에서 {@link DuplicateKeyException}이
 * 발생한다면 더 이상 "예상된 race condition"이 아니라 진짜 무결성 위반(버그)입니다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CompanyCompatibilityJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * CompanyCompatibility root 엔티티를 삽입합니다.
     *
     * <p>userProfile+company+role 단위 분산락 안에서만 호출되므로 동일 월 중복 삽입은
     * 발생하지 않아야 합니다. 그럼에도 {@link DuplicateKeyException}이 발생하면 락 밖 경로나
     * 데이터 이관 등에서 비롯된 진짜 무결성 위반이므로 {@link DataAccessException}으로 전파합니다.
     *
     * @param entity 저장할 CompanyCompatibility (compatibilityMonth 필드 포함)
     */
    public void insert(CompanyCompatibility entity) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO company_compatibility " +
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
        } catch (DuplicateKeyException e) {
            throw new DataAccessException(
                    "분산락 보호 하에서 예기치 못한 CompanyCompatibility UNIQUE 제약 위반이 발생했습니다: "
                            + "compatibilityMonth=" + entity.getCompatibilityMonth(), e);
        }
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
