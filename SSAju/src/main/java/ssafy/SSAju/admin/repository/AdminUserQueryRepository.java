package ssafy.SSAju.admin.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.admin.dto.UserSearchDTO;
import ssafy.SSAju.entity.enums.UserStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AdminUserQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Keyset(커서) 기반 유저 목록 조회.
     * lastId가 null이면 첫 페이지, null이 아니면 해당 id 이후 데이터를 조회.
     * OFFSET 방식과 달리 깊은 페이지에서도 일정한 성능을 보장.
     * User 엔티티의 @SQLRestriction을 우회하기 위해 native query 사용.
     */
    public List<UserSearchDTO> findUsersByKeyset(
            String email, String name, Instant joinDateFrom, Instant joinDateTo,
            UserStatus status, Long lastId, int limit) {

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (email != null && !email.isBlank()) {
            where.append(" AND u.email LIKE ?");
            params.add("%" + email + "%");
        }
        if (name != null && !name.isBlank()) {
            where.append(" AND u.name LIKE ?");
            params.add("%" + name + "%");
        }
        if (joinDateFrom != null) {
            where.append(" AND u.created_at >= ?");
            params.add(Timestamp.from(joinDateFrom));
        }
        if (joinDateTo != null) {
            where.append(" AND u.created_at < ?");
            params.add(Timestamp.from(joinDateTo));
        }
        if (status != null) {
            where.append(" AND u.status = ?");
            params.add(status.name());
        }
        if (lastId != null) {
            where.append(" AND u.id < ?");
            params.add(lastId);
        }

        String sql = """
                SELECT u.id, u.email, u.name, u.status, u.created_at, u.deleted_at,
                       COALESCE(ac.total, 0) AS total_analysis_count
                FROM users u
                LEFT JOIN (
                    SELECT user_id, COUNT(*) AS total
                    FROM (
                        SELECT usa.user_id FROM saju_result sr
                            JOIN user_saju_access usa ON usa.saju_result_id = sr.id
                        UNION ALL
                        SELECT usa.user_id FROM career_consultation cc
                            JOIN saju_result sr ON cc.saju_result_id = sr.id
                            JOIN user_saju_access usa ON usa.saju_result_id = sr.id
                        UNION ALL
                        SELECT user_id FROM company_compatibility
                    ) all_analyses
                    GROUP BY user_id
                ) ac ON u.id = ac.user_id
                """ + where + " ORDER BY u.id DESC LIMIT ?";

        params.add(limit);

        return jdbcTemplate.query(sql, (rs, rowNum) -> new UserSearchDTO(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("name"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                UserStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("deleted_at") != null ? rs.getTimestamp("deleted_at").toInstant() : null,
                rs.getLong("total_analysis_count")
        ), params.toArray());
    }

    public Optional<UserSearchDTO> findUserById(Long userId) {
        String sql = """
                SELECT u.id, u.email, u.name, u.status, u.created_at, u.deleted_at
                FROM users u WHERE u.id = ?
                """;

        List<UserSearchDTO> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            long analysisCount = countAnalysisByUser(userId);
            return new UserSearchDTO(
                    rs.getLong("id"),
                    rs.getString("email"),
                    rs.getString("name"),
                    rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                    UserStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("deleted_at") != null ? rs.getTimestamp("deleted_at").toInstant() : null,
                    analysisCount
            );
        }, userId);

        return rows.stream().findFirst();
    }

    private long countAnalysisByUser(Long userId) {
        String countSql = """
                SELECT COUNT(*) FROM (
                    SELECT sr.id FROM saju_result sr
                    JOIN user_saju_access usa ON usa.saju_result_id = sr.id WHERE usa.user_id = ?
                    UNION ALL
                    SELECT cc.id FROM career_consultation cc
                    JOIN saju_result sr ON cc.saju_result_id = sr.id
                    JOIN user_saju_access usa ON usa.saju_result_id = sr.id WHERE usa.user_id = ?
                    UNION ALL
                    SELECT id FROM company_compatibility WHERE user_id = ?
                ) t
                """;
        Long count = jdbcTemplate.queryForObject(countSql, Long.class, userId, userId, userId);
        return count != null ? count : 0L;
    }
}
