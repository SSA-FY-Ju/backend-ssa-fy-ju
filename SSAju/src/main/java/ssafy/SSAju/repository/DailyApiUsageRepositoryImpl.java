package ssafy.SSAju.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public class DailyApiUsageRepositoryImpl implements DailyApiUsageCustomRepository {

    private final JdbcTemplate jdbcTemplate;

    public DailyApiUsageRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int upsertUsageIfUnderLimit(Long userId, LocalDate date, int limit) {
        String sql = """
                INSERT INTO daily_api_usage (user_id, usage_date, request_count, created_at)
                VALUES (?, ?, 1, NOW())
                ON DUPLICATE KEY UPDATE
                    request_count = IF(request_count < ?, request_count + 1, request_count)
                """;
        int affectedRows = jdbcTemplate.update(sql, userId, date, limit);

        // MySQL Connector/J 8.x 기준: INSERT=1, UPDATE(change)=2, UPDATE(no-op/한도초과)=0
        // MySQL Connector/J 9.x 기준: INSERT=1, UPDATE(change)=2, UPDATE(no-op)=1 (변경됨)
        // affectedRows=2 는 양쪽 모두 "증가 성공"을 의미하므로 그대로 반환
        // affectedRows=0 는 8.x에서 "한도 초과"를 의미하므로 그대로 반환
        if (affectedRows != 1) {
            return affectedRows;
        }

        // affectedRows=1 은 9.x에서 새 INSERT(첫 요청)와 no-op UPDATE(한도 초과) 모두에 해당
        // 실제 request_count를 조회하여 구분: count >= limit이면 no-op → 0 반환
        Integer count = jdbcTemplate.queryForObject(
                "SELECT request_count FROM daily_api_usage WHERE user_id = ? AND usage_date = ?",
                Integer.class, userId, date
        );
        return (count != null && count >= limit) ? 0 : affectedRows;
    }
}
