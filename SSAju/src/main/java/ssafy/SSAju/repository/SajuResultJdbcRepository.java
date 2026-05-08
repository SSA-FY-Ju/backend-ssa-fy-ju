package ssafy.SSAju.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.career.entity.SajuResult;

import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class SajuResultJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public int insertOrIgnore(SajuResult sajuResult) {
        Objects.requireNonNull(sajuResult, "sajuResult는 null일 수 없습니다.");
        Objects.requireNonNull(sajuResult.getUserProfile(), "SajuResult의 UserProfile은 null일 수 없습니다.");
        return jdbcTemplate.update(
                "INSERT IGNORE INTO saju_result (user_profile_id, fetched_at) VALUES (?, ?)",
                sajuResult.getUserProfile().getId(),
                sajuResult.getFetchedAt()
        );
    }
}
