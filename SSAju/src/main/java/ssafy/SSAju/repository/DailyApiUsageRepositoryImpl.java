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
        return jdbcTemplate.update(sql, userId, date, limit);
    }
}
