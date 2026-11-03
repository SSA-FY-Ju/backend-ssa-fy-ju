package ssafy.SSAju.admin.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.admin.dto.AnalyticsDetailDTO;
import ssafy.SSAju.admin.dto.AnalyticsListDTO;
import ssafy.SSAju.admin.service.AdminBaseService;
import ssafy.SSAju.career.enums.AnalysisType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

/**
 * 관리자 분석 조회. 세 유형이 {@code saju} 한 테이블에 모이면서 UNION 없이 단일 집계다.
 *
 * <p>소유자는 {@code saju.user_id}를 직접 읽는다 — User 엔티티로 조인하면
 * {@code @SQLRestriction("deleted_at IS NULL")} 때문에 탈퇴 사용자의 분석이 관리자 화면에서
 * 사라진다. 계정 표시가 필요하면 그때만 SQL로 users를 조인해 대체된 이름/이메일을 읽는다.
 *
 * <p>{@code created_at}은 UTC로 저장되므로 경계 파라미터와 조회 모두 UTC Calendar를 사용한다.
 * JVM 기본 시간대에 의존하면 서울 날짜 경계가 한 칸씩 밀린다.
 */
@Repository
@RequiredArgsConstructor
public class AdminAnalyticsQueryRepository {

    private static final Calendar UTC_CALENDAR_PROTOTYPE =
            Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    private final JdbcTemplate jdbcTemplate;

    public List<AnalyticsListDTO> findAnalyticsByDateAndType(
            String analysisType, LocalDate dateFrom, LocalDate dateTo, int page, int size) {
        Instant fromInstant = dateFrom.atStartOfDay(AdminBaseService.SEOUL_ZONE).toInstant();
        Instant toInstant = dateTo.plusDays(1).atStartOfDay(AdminBaseService.SEOUL_ZONE).toInstant();
        int offset = page * size;

        String typeCondition = analysisType != null ? " AND s.analysis_type = ?" : "";
        String sql = """
                SELECT s.id, s.user_id, s.analysis_type, s.created_at
                FROM saju s
                WHERE s.created_at >= ? AND s.created_at < ?"""
                + typeCondition
                + " ORDER BY s.created_at DESC, s.id DESC LIMIT ? OFFSET ?";

        return jdbcTemplate.query(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(sql);
                    int index = bindRange(ps, fromInstant, toInstant);
                    if (analysisType != null) {
                        ps.setString(index++, analysisType);
                    }
                    ps.setInt(index++, size);
                    ps.setInt(index, offset);
                    return ps;
                },
                (rs, rowNum) -> new AnalyticsListDTO(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("analysis_type"),
                        readCreatedAt(rs)));
    }

    /**
     * 분석 ID·유형·소유자가 모두 일치하는 한 건만 조회한다. 목록에서 클릭한 행의 userId를 그대로
     * 받아 다른 사용자의 결과가 섞이지 않게 한다.
     */
    public Optional<AnalyticsDetailDTO> findAnalyticsById(Long id, String analysisType, Long userId) {
        String sql = """
                SELECT s.id, s.user_id, s.analysis_type, s.result_json, s.created_at
                FROM saju s
                WHERE s.id = ? AND s.analysis_type = ? AND s.user_id = ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AnalyticsDetailDTO(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("analysis_type"),
                rs.getString("result_json"),
                readCreatedAt(rs)
        ), id, analysisType, userId).stream().findFirst();
    }

    /** 서울 날짜 하루의 유형별 분석 수. 새 분석 한 행이 성공 한 건이다. */
    public Map<String, Long> findDailyAnalysisSummary(LocalDate date) {
        Instant fromInstant = date.atStartOfDay(AdminBaseService.SEOUL_ZONE).toInstant();
        Instant toInstant = date.plusDays(1).atStartOfDay(AdminBaseService.SEOUL_ZONE).toInstant();

        Map<String, Long> result = new HashMap<>();
        for (AnalysisType type : AnalysisType.values()) {
            result.put(type.name(), 0L);
        }

        jdbcTemplate.query(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement("""
                            SELECT s.analysis_type, COUNT(*) AS total
                            FROM saju s
                            WHERE s.created_at >= ? AND s.created_at < ?
                            GROUP BY s.analysis_type
                            """);
                    bindRange(ps, fromInstant, toInstant);
                    return ps;
                },
                rs -> {
                    result.put(rs.getString("analysis_type"), rs.getLong("total"));
                });
        return result;
    }

    private int bindRange(PreparedStatement ps, Instant from, Instant to) throws SQLException {
        Calendar utc = (Calendar) UTC_CALENDAR_PROTOTYPE.clone();
        ps.setTimestamp(1, Timestamp.from(from), utc);
        ps.setTimestamp(2, Timestamp.from(to), utc);
        return 3;
    }

    private Instant readCreatedAt(ResultSet rs) throws SQLException {
        return rs.getTimestamp("created_at", (Calendar) UTC_CALENDAR_PROTOTYPE.clone()).toInstant();
    }
}
