package ssafy.SSAju.admin.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
     * Soft Delete된 유저 포함 검색.
     * User 엔티티의 @SQLRestriction("deleted_at IS NULL")을 우회하기 위해 native query 사용.
     */
    public Page<UserSearchDTO> findUsersByFilters(
            String email, String name, Instant joinDateFrom, Instant joinDateTo,
            UserStatus status, Pageable pageable) {

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

        String base = """
                SELECT u.id, u.email, u.name, u.status, u.created_at, u.deleted_at,
                       COALESCE(ac.total, 0) AS total_analysis_count
                FROM users u
                LEFT JOIN (
                    SELECT user_id, COUNT(*) AS total
                    FROM (
                        SELECT user_id FROM saju_result
                        UNION ALL
                        SELECT sr.user_id FROM career_consultation cc
                            JOIN saju_result sr ON cc.saju_result_id = sr.id
                        UNION ALL
                        SELECT user_id FROM company_compatibility
                    ) all_analyses
                    GROUP BY user_id
                ) ac ON u.id = ac.user_id
                """ + where;
        String countSql = "SELECT COUNT(*) FROM (" + base + ") cnt";
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());

        String listSql = base + " ORDER BY u.created_at DESC LIMIT ? OFFSET ?";
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(pageable.getPageSize());
        listParams.add(pageable.getOffset());

        List<UserSearchDTO> content = jdbcTemplate.query(listSql, (rs, rowNum) -> new UserSearchDTO(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("name"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                UserStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("deleted_at") != null ? rs.getTimestamp("deleted_at").toInstant() : null,
                rs.getLong("total_analysis_count")
        ), listParams.toArray());

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
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
                    SELECT id FROM saju_result WHERE user_id = ?
                    UNION ALL
                    SELECT cc.id FROM career_consultation cc
                    JOIN saju_result sr ON cc.saju_result_id = sr.id WHERE sr.user_id = ?
                    UNION ALL
                    SELECT id FROM company_compatibility WHERE user_id = ?
                ) t
                """;
        Long count = jdbcTemplate.queryForObject(countSql, Long.class, userId, userId, userId);
        return count != null ? count : 0L;
    }
}
