package ssafy.SSAju.admin.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.admin.dto.AnalyticsDetailDTO;
import ssafy.SSAju.admin.dto.AnalyticsListDTO;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AdminAnalyticsQueryRepository {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String SAJU = "SAJU";
    private static final String CAREER_CONSULTATION = "CAREER_CONSULTATION";
    private static final String COMPANY_COMPATIBILITY = "COMPANY_COMPATIBILITY";

    private final JdbcTemplate jdbcTemplate;

    private static final String UNION_LIST_QUERY = """
            SELECT 'SAJU' AS analysis_type, sr.id, sr.user_id, sr.fetched_at AS created_at
            FROM saju_result sr
            WHERE (:type IS NULL OR :type = 'SAJU')
              AND sr.fetched_at >= :dateFrom AND sr.fetched_at < :dateTo

            UNION ALL

            SELECT 'CAREER_CONSULTATION' AS analysis_type, cc.id, sr.user_id, cc.generated_at AS created_at
            FROM career_consultation cc
            JOIN saju_result sr ON cc.saju_result_id = sr.id
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
        var fromInstant = dateFrom.atStartOfDay(SEOUL).toInstant();
        var toInstant = dateTo.plusDays(1).atStartOfDay(SEOUL).toInstant();
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
        return switch (analysisType) {
            case SAJU -> findSajuDetail(id);
            case CAREER_CONSULTATION -> findConsultationDetail(id);
            case COMPANY_COMPATIBILITY -> findCompatibilityDetail(id);
            default -> Optional.empty();
        };
    }

    public Map<String, Long> findDailyAnalysisSummary(LocalDate date) {
        var fromInstant = date.atStartOfDay(SEOUL).toInstant();
        var toInstant = date.plusDays(1).atStartOfDay(SEOUL).toInstant();

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
                        SELECT 'SAJU' AS analysis_type, sr.id, sr.user_id, sr.fetched_at AS created_at
                        FROM saju_result sr
                        UNION ALL
                        SELECT 'CAREER_CONSULTATION', cc.id, sr.user_id, cc.generated_at
                        FROM career_consultation cc JOIN saju_result sr ON cc.saju_result_id = sr.id
                        UNION ALL
                        SELECT 'COMPANY_COMPATIBILITY', compat.id, compat.user_id, compat.created_at
                        FROM company_compatibility compat
                    ) combined
                    WHERE created_at >= ? AND created_at < ?
                    ORDER BY created_at DESC LIMIT ? OFFSET ?
                    """;
        }
        return switch (analysisType) {
            case SAJU -> "SELECT 'SAJU' AS analysis_type, id, user_id, fetched_at AS created_at FROM saju_result WHERE fetched_at >= ? AND fetched_at < ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
            case CAREER_CONSULTATION -> "SELECT 'CAREER_CONSULTATION' AS analysis_type, cc.id, sr.user_id, cc.generated_at AS created_at FROM career_consultation cc JOIN saju_result sr ON cc.saju_result_id = sr.id WHERE cc.generated_at >= ? AND cc.generated_at < ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
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

    private Optional<AnalyticsDetailDTO> findSajuDetail(Long id) {
        String sql = "SELECT sr.id, sr.user_id, sr.fetched_at AS created_at FROM saju_result sr WHERE sr.id = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AnalyticsDetailDTO(
                rs.getLong("id"),
                rs.getLong("user_id"),
                SAJU,
                null,
                rs.getTimestamp("created_at").toInstant()
        ), id).stream().findFirst();
    }

    private Optional<AnalyticsDetailDTO> findConsultationDetail(Long id) {
        String sql = """
                SELECT cc.id, sr.user_id, cc.generated_at AS created_at
                FROM career_consultation cc
                JOIN saju_result sr ON cc.saju_result_id = sr.id
                WHERE cc.id = ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AnalyticsDetailDTO(
                rs.getLong("id"),
                rs.getLong("user_id"),
                CAREER_CONSULTATION,
                null,
                rs.getTimestamp("created_at").toInstant()
        ), id).stream().findFirst();
    }

    private Optional<AnalyticsDetailDTO> findCompatibilityDetail(Long id) {
        String sql = "SELECT id, user_id, created_at FROM company_compatibility WHERE id = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AnalyticsDetailDTO(
                rs.getLong("id"),
                rs.getLong("user_id"),
                COMPANY_COMPATIBILITY,
                null,
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
