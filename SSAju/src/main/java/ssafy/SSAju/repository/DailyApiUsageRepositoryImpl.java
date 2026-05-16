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
        // MySQL Connector/J 8.x 기본값(useAffectedRows=false) 기준:
        // - INSERT: affectedRows=1, UPDATE 성공: affectedRows=2, no-op UPDATE(한도도달): affectedRows=0
        // ⚠️ DB_URL에 ?useAffectedRows=true 플래그 추가 금지 (affectedRows 의미 변경)
        String sql = """
                INSERT INTO daily_api_usage (user_id, usage_date, request_count, created_at)
                VALUES (?, ?, 1, NOW())
                ON DUPLICATE KEY UPDATE
                    request_count = IF(request_count < ?, request_count + 1, request_count)
                """;
        return jdbcTemplate.update(sql, userId, date, limit);
    }
}
