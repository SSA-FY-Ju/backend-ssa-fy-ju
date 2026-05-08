package ssafy.SSAju.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.career.entity.SajuResult;

@Repository
@RequiredArgsConstructor
public class SajuResultJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public int insertOrIgnore(SajuResult sajuResult) {
        return jdbcTemplate.update(
                "INSERT IGNORE INTO saju_result (user_profile_id, fetched_at) VALUES (?, ?)",
                sajuResult.getUserProfile().getId(),
                sajuResult.getFetchedAt()
        );
    }
}
