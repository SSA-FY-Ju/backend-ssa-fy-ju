package ssafy.SSAju.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.dto.response.UserAnalysisDto;

import java.util.List;

/**
 * 마이페이지 분석 이력 조회 리포지토리.
 *
 * <p>SajuResult(SAJU), CareerFortune(CAREER_FORTUNE), CompanyCompatibility(COMPANY_COMPATIBILITY)를
 * UNION ALL 쿼리로 통합하여 최근 1년 분석 이력을 반환합니다.
 * JPA UNION 미지원 문제를 우회하기 위해 JdbcTemplate을 사용합니다.
 */
@Repository
@RequiredArgsConstructor
public class AnalysisHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String UNION_QUERY = """
            SELECT 'SAJU' AS type, sr.id AS analysis_id, u.name AS target_name,
                   up.birth_date, sr.fetched_at AS created_at,
                   usf.satisfaction_status
            FROM saju_result sr
            JOIN user_profile up ON sr.user_profile_id = up.id
            JOIN users u ON sr.user_id = u.id
            LEFT JOIN user_satisfaction_feedback usf
                ON usf.saju_result_id = sr.id
                AND usf.user_id = sr.user_id
                AND usf.feedback_type = 'SAJU'
            WHERE sr.user_id = ?
              AND sr.fetched_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR)

            UNION ALL

            SELECT 'CAREER_FORTUNE', cf.id, u.name, up.birth_date,
                   cf.created_at, usf.satisfaction_status
            FROM career_fortune cf
            JOIN saju_result sr ON cf.saju_result_id = sr.id
            JOIN user_profile up ON sr.user_profile_id = up.id
            JOIN users u ON sr.user_id = u.id
            LEFT JOIN user_satisfaction_feedback usf
                ON usf.saju_result_id = cf.saju_result_id
                AND usf.user_id = sr.user_id
                AND usf.feedback_type = 'CAREER_FORTUNE'
            WHERE sr.user_id = ?
              AND cf.created_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR)

            UNION ALL

            SELECT 'COMPANY_COMPATIBILITY', cc.id, cc.company_name, up.birth_date,
                   cc.created_at, NULL
            FROM company_compatibility cc
            JOIN user_profile up ON cc.user_profile_id = up.id
            WHERE cc.user_id = ?
              AND cc.created_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR)
            """;

    private static final String COUNT_QUERY = """
            SELECT COUNT(*) FROM (
                SELECT sr.id FROM saju_result sr
                WHERE sr.user_id = ?
                  AND sr.fetched_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR)

                UNION ALL

                SELECT cf.id FROM career_fortune cf
                JOIN saju_result sr ON cf.saju_result_id = sr.id
                WHERE sr.user_id = ?
                  AND cf.created_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR)

                UNION ALL

                SELECT cc.id FROM company_compatibility cc
                WHERE cc.user_id = ?
                  AND cc.created_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR)
            ) total
            """;

    private static final String COUNT_BY_TYPE_QUERY = """
            SELECT COUNT(*) FROM (
                SELECT sr.id, 'SAJU' AS type FROM saju_result sr
                WHERE sr.user_id = ?
                  AND sr.fetched_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR)

                UNION ALL

                SELECT cf.id, 'CAREER_FORTUNE' FROM career_fortune cf
                JOIN saju_result sr ON cf.saju_result_id = sr.id
                WHERE sr.user_id = ?
                  AND cf.created_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR)

                UNION ALL

                SELECT cc.id, 'COMPANY_COMPATIBILITY' FROM company_compatibility cc
                WHERE cc.user_id = ?
                  AND cc.created_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR)
            ) filtered WHERE filtered.type = ?
            """;

    public List<UserAnalysisDto> findAllByUserId(Long userId, int page, int size) {
        String sql = UNION_QUERY + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        int offset = page * size;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new UserAnalysisDto(
                        rs.getString("type"),
                        rs.getLong("analysis_id"),
                        rs.getString("target_name"),
                        rs.getDate("birth_date").toLocalDate(),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getString("satisfaction_status")
                ),
                userId, userId, userId, size, offset);
    }

    public List<UserAnalysisDto> findAllByUserIdAndType(Long userId, String type, int page, int size) {
        String typeSql = "SELECT * FROM (" + UNION_QUERY + ") AS combined_result " +
                "WHERE type = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        int offset = page * size;
        return jdbcTemplate.query(
                typeSql,
                (rs, rowNum) -> new UserAnalysisDto(
                        rs.getString("type"),
                        rs.getLong("analysis_id"),
                        rs.getString("target_name"),
                        rs.getDate("birth_date").toLocalDate(),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getString("satisfaction_status")
                ),
                userId, userId, userId, type, size, offset);
    }

    public long countAllByUserId(Long userId) {
        Long count = jdbcTemplate.queryForObject(COUNT_QUERY, Long.class, userId, userId, userId);
        return count != null ? count : 0L;
    }

    public long countAllByUserIdAndType(Long userId, String type) {
        Long count = jdbcTemplate.queryForObject(COUNT_BY_TYPE_QUERY, Long.class,
                userId, userId, userId, type);
        return count != null ? count : 0L;
    }
}
