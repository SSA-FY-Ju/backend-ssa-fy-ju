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

        // JDBC URL에 useAffectedRows=true 설정 필수 (DB_URL 환경변수, application.yaml 모두)
        // 예: jdbc:mysql://localhost:3306/ssaju?useAffectedRows=true&...
        // useAffectedRows=true → MySQL이 "changed rows"를 반환:
        //   1 = INSERT 성공 (첫 요청, 새 행 삽입)
        //   2 = UPDATE 성공 (기존 행 증가)
        //   0 = UPDATE no-op (request_count >= limit, 한도 초과)
        // → 단일 쿼리 결과로 원자적 판정 가능, 후속 SELECT 불필요 (TOCTOU 없음)
        return jdbcTemplate.update(sql, userId, date, limit);
    }

    @Override
    public int restoreUsage(Long userId, LocalDate date) {
        String sql = """
                UPDATE daily_api_usage
                SET request_count = GREATEST(request_count - 1, 0)
                WHERE user_id = ? AND usage_date = ?
                """;

        return jdbcTemplate.update(sql, userId, date);
    }
}
