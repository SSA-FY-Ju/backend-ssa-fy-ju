package ssafy.SSAju.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.career.converter.AnalysisSnapshotCodec;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.dto.response.UserAnalysisDto;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * 마이페이지 분석 이력 조회 리포지토리.
 *
 * <p>세 유형이 {@code saju} 한 테이블로 통합되어 UNION 없이 단일 조회다. 소유권은 공유 접근 관계가
 * 아니라 {@code saju.user_id}로 직접 판단한다.
 *
 * <p>기간 제한을 두지 않는다 — 목록과 count가 같은 조건을 써야 페이지 수와 실제 행이 어긋나지 않고,
 * 명세상 소유자의 전체 이력을 제공한다.
 *
 * <p>{@code created_at}은 UTC로 저장되므로 JVM 기본 시간대에 의존하지 않도록 UTC Calendar를 넘겨
 * 읽는다(저장 계약).
 */
@Repository
@RequiredArgsConstructor
public class AnalysisHistoryRepository {

    private static final Calendar UTC_CALENDAR_PROTOTYPE =
            Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    private static final String LIST_QUERY = """
            SELECT s.analysis_type AS type, s.id AS analysis_id, u.name AS user_name,
                   up.birth_date AS birth_date, s.created_at AS created_at, s.input_json AS input_json
            FROM saju s
            JOIN manse m ON s.manse_id = m.id
            JOIN user_profile up ON m.user_profile_id = up.id
            JOIN users u ON s.user_id = u.id
            WHERE s.user_id = ?
            """;

    private static final String ORDER_AND_PAGE = " ORDER BY s.created_at DESC, s.id DESC LIMIT ? OFFSET ?";

    private static final String TYPE_CONDITION = " AND s.analysis_type = ?";

    private static final String COUNT_QUERY = "SELECT COUNT(*) FROM saju s WHERE s.user_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final AnalysisSnapshotCodec snapshotCodec;

    public List<UserAnalysisDto> findAllByUserId(Long userId, int page, int size) {
        return jdbcTemplate.query(
                LIST_QUERY + ORDER_AND_PAGE,
                this::mapRow,
                userId, size, page * size);
    }

    public List<UserAnalysisDto> findAllByUserIdAndType(Long userId, String type, int page, int size) {
        return jdbcTemplate.query(
                LIST_QUERY + TYPE_CONDITION + ORDER_AND_PAGE,
                this::mapRow,
                userId, type, size, page * size);
    }

    public long countAllByUserId(Long userId) {
        Long count = jdbcTemplate.queryForObject(COUNT_QUERY, Long.class, userId);
        return count != null ? count : 0L;
    }

    public long countAllByUserIdAndType(Long userId, String type) {
        Long count = jdbcTemplate.queryForObject(
                COUNT_QUERY + " AND s.analysis_type = ?", Long.class, userId, type);
        return count != null ? count : 0L;
    }

    /**
     * 궁합은 기존 목록처럼 회사명을 대상 이름으로 보여준다. 회사명은 입력 스냅샷에 들어 있으므로
     * 행마다 추가 조회 없이 같은 결과 집합에서 읽어 변환한다.
     */
    private UserAnalysisDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        AnalysisType type = AnalysisType.valueOf(rs.getString("type"));
        String targetName = type == AnalysisType.COMPANY_COMPATIBILITY
                ? companyNameOf(rs.getString("input_json"))
                : rs.getString("user_name");

        return new UserAnalysisDto(
                type.name(),
                rs.getLong("analysis_id"),
                targetName,
                rs.getDate("birth_date").toLocalDate(),
                rs.getTimestamp("created_at", (Calendar) UTC_CALENDAR_PROTOTYPE.clone()).toInstant());
    }

    private String companyNameOf(String inputJson) {
        AnalysisPayload.Input input =
                snapshotCodec.readInput(AnalysisType.COMPANY_COMPATIBILITY, inputJson);
        return ((AnalysisPayload.CompatibilityInput) input).companyName();
    }
}
