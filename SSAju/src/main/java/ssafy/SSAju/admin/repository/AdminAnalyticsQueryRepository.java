package ssafy.SSAju.admin.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.admin.dto.AnalyticsDetailDTO;
import ssafy.SSAju.admin.dto.AnalyticsListDTO;
import ssafy.SSAju.admin.service.AdminBaseService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AdminAnalyticsQueryRepository {

    private static final String SAJU = "SAJU";
    private static final String CAREER_CONSULTATION = "CAREER_CONSULTATION";
    private static final String COMPANY_COMPATIBILITY = "COMPANY_COMPATIBILITY";

    private final JdbcTemplate jdbcTemplate;

    // B1: SajuResult는 더 이상 user_id 컬럼을 갖지 않으므로(여러 사용자가 공유하는 정본),
    // SAJU/CAREER_CONSULTATION 행의 user_id는 user_saju_access 매핑을 통해 얻는다.
    // (참고: 이 상수는 현재 어떤 메서드에서도 참조되지 않는 죽은 코드다 — 실제 쿼리는
    // buildListSql()이 만든다. 향후 참조될 경우를 대비해 스키마와 일치시켜 둔다.)
    private static final String UNION_LIST_QUERY = """
            SELECT 'SAJU' AS analysis_type, sr.id, usa.user_id, sr.fetched_at AS created_at
            FROM saju_result sr
            JOIN user_saju_access usa ON usa.saju_result_id = sr.id
            WHERE (:type IS NULL OR :type = 'SAJU')
              AND sr.fetched_at >= :dateFrom AND sr.fetched_at < :dateTo

            UNION ALL

            SELECT 'CAREER_CONSULTATION' AS analysis_type, cc.id, usa.user_id, cc.generated_at AS created_at
            FROM career_consultation cc
            JOIN saju_result sr ON cc.saju_result_id = sr.id
            JOIN user_saju_access usa ON usa.saju_result_id = sr.id
            WHERE (:type IS NULL OR :type = 'CAREER_CONSULTATION')
              AND cc.generated_at >= :dateFrom AND cc.generated_at < :dateTo

            UNION ALL

            SELECT 'COMPANY_COMPATIBILITY' AS analysis_type, compat.id, compat.user_id, compat.created_at
            FROM company_compatibility compat
            WHERE (:type IS NULL OR :type = 'COMPANY_COMPATIBILITY')
              AND compat.created_at >= :dateFrom AND compat.created_at < :dateTo

            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

    public List<AnalyticsListDTO> findAnalyticsByDateAndType(
            String analysisType, LocalDate dateFrom, LocalDate dateTo, int page, int size) {
        var fromInstant = dateFrom.atStartOfDay(AdminBaseService.SEOUL_ZONE).toInstant();
        var toInstant = dateTo.plusDays(1).atStartOfDay(AdminBaseService.SEOUL_ZONE).toInstant();
        int offset = page * size;

        String sql = buildListSql(analysisType);
        List<Object> params = buildListParams(analysisType, fromInstant, toInstant, size, offset);

        return jdbcTemplate.query(sql, params.toArray(), (rs, rowNum) -> new AnalyticsListDTO(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("analysis_type"),
                rs.getTimestamp("created_at").toInstant()
        ));
    }

    public Optional<AnalyticsDetailDTO> findAnalyticsById(Long id, String analysisType) {
        if (analysisType == null) return Optional.empty();
        return switch (analysisType) {
            case SAJU -> findSajuDetail(id);
            case CAREER_CONSULTATION -> findConsultationDetail(id);
            case COMPANY_COMPATIBILITY -> findCompatibilityDetail(id);
            default -> Optional.empty();
        };
    }

    public Map<String, Long> findDailyAnalysisSummary(LocalDate date) {
        var fromInstant = date.atStartOfDay(AdminBaseService.SEOUL_ZONE).toInstant();
        var toInstant = date.plusDays(1).atStartOfDay(AdminBaseService.SEOUL_ZONE).toInstant();

        Map<String, Long> result = new HashMap<>();
        result.put(SAJU, countSaju(fromInstant, toInstant));
        result.put(CAREER_CONSULTATION, countConsultation(fromInstant, toInstant));
        result.put(COMPANY_COMPATIBILITY, countCompatibility(fromInstant, toInstant));
        return result;
    }

    private String buildListSql(String analysisType) {
        if (analysisType == null) {
            return """
                    SELECT * FROM (
                        SELECT 'SAJU' AS analysis_type, sr.id, usa.user_id, sr.fetched_at AS created_at
                        FROM saju_result sr
                        JOIN user_saju_access usa ON usa.saju_result_id = sr.id
                        UNION ALL
                        SELECT 'CAREER_CONSULTATION', cc.id, usa.user_id, cc.generated_at
                        FROM career_consultation cc
                        JOIN saju_result sr ON cc.saju_result_id = sr.id
                        JOIN user_saju_access usa ON usa.saju_result_id = sr.id
                        UNION ALL
                        SELECT 'COMPANY_COMPATIBILITY', compat.id, compat.user_id, compat.created_at
                        FROM company_compatibility compat
                    ) combined
                    WHERE created_at >= ? AND created_at < ?
                    ORDER BY created_at DESC LIMIT ? OFFSET ?
                    """;
        }
        return switch (analysisType) {
            case SAJU -> "SELECT 'SAJU' AS analysis_type, sr.id, usa.user_id, sr.fetched_at AS created_at FROM saju_result sr JOIN user_saju_access usa ON usa.saju_result_id = sr.id WHERE sr.fetched_at >= ? AND sr.fetched_at < ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
            case CAREER_CONSULTATION -> "SELECT 'CAREER_CONSULTATION' AS analysis_type, cc.id, usa.user_id, cc.generated_at AS created_at FROM career_consultation cc JOIN saju_result sr ON cc.saju_result_id = sr.id JOIN user_saju_access usa ON usa.saju_result_id = sr.id WHERE cc.generated_at >= ? AND cc.generated_at < ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
            case COMPANY_COMPATIBILITY -> "SELECT 'COMPANY_COMPATIBILITY' AS analysis_type, id, user_id, created_at FROM company_compatibility WHERE created_at >= ? AND created_at < ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
            default -> throw new IllegalArgumentException("Unknown analysisType: " + analysisType);
        };
    }

    private List<Object> buildListParams(String analysisType, Object fromInstant, Object toInstant, int size, int offset) {
        List<Object> params = new ArrayList<>();
        params.add(fromInstant);
        params.add(toInstant);
        params.add(size);
        params.add(offset);
        return params;
    }

    /**
     * B1: SajuResult는 여러 사용자가 공유하는 정본이라 sr.id 하나에 user_saju_access가
     * 여럿 붙을 수 있다. 이 화면의 user_id는 상세 모달에 표시되는 정보성 텍스트일 뿐
     * "제한 조정" 등 실제 액션에는 쓰이지 않으므로(그 액션은 목록 행의 user_id를 그대로
     * 사용한다), 최초 접근자(usa.created_at ASC LIMIT 1)를 대표값으로 보여준다.
     */
    private Optional<AnalyticsDetailDTO> findSajuDetail(Long id) {
        String sql = """
                SELECT sr.id, usa.user_id,
                       CONCAT(sfd.year_pillar, sfd.month_pillar, sfd.day_pillar, sfd.hour_pillar) AS json_data,
                       sr.fetched_at AS created_at
                FROM saju_result sr
                LEFT JOIN saju_full_data sfd ON sfd.saju_result_id = sr.id
                JOIN user_saju_access usa ON usa.saju_result_id = sr.id
                WHERE sr.id = ?
                ORDER BY usa.created_at ASC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AnalyticsDetailDTO(
                rs.getLong("id"),
                rs.getLong("user_id"),
                SAJU,
                rs.getString("json_data"),
                rs.getTimestamp("created_at").toInstant()
        ), id).stream().findFirst();
    }

    /** B1: SajuResult 공유로 인한 대표 user_id 선정 기준은 {@link #findSajuDetail}과 동일. */
    private Optional<AnalyticsDetailDTO> findConsultationDetail(Long id) {
        String sql = """
                SELECT cc.id, usa.user_id, cc.day_master_description AS json_data, cc.generated_at AS created_at
                FROM career_consultation cc
                JOIN saju_result sr ON cc.saju_result_id = sr.id
                JOIN user_saju_access usa ON usa.saju_result_id = sr.id
                WHERE cc.id = ?
                ORDER BY usa.created_at ASC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AnalyticsDetailDTO(
                rs.getLong("id"),
                rs.getLong("user_id"),
                CAREER_CONSULTATION,
                rs.getString("json_data"),
                rs.getTimestamp("created_at").toInstant()
        ), id).stream().findFirst();
    }

    private Optional<AnalyticsDetailDTO> findCompatibilityDetail(Long id) {
        String sql = """
                SELECT id, user_id, summary AS json_data, created_at
                FROM company_compatibility
                WHERE id = ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AnalyticsDetailDTO(
                rs.getLong("id"),
                rs.getLong("user_id"),
                COMPANY_COMPATIBILITY,
                rs.getString("json_data"),
                rs.getTimestamp("created_at").toInstant()
        ), id).stream().findFirst();
    }

    private long countSaju(Object from, Object to) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM saju_result WHERE fetched_at >= ? AND fetched_at < ?", Long.class, from, to);
        return count != null ? count : 0L;
    }

    private long countConsultation(Object from, Object to) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM career_consultation WHERE generated_at >= ? AND generated_at < ?", Long.class, from, to);
        return count != null ? count : 0L;
    }

    private long countCompatibility(Object from, Object to) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company_compatibility WHERE created_at >= ? AND created_at < ?", Long.class, from, to);
        return count != null ? count : 0L;
    }
}
