package ssafy.SSAju.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.career.entity.SajuResult;

@Repository
@RequiredArgsConstructor
public class SajuResultJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    // TODO (T049): version 기반 이력 관리로 전환
    // - SajuResult entity에 version, analyzedAt 필드 추가
    // - insertNewVersion() 메서드 추가
    // - UNIQUE 제약을 (user_id, version) 기준으로 변경
    public int insertOrIgnore(SajuResult sajuResult) {
        return jdbcTemplate.update(
                "INSERT IGNORE INTO saju_result (user_profile_id, user_id, fetched_at) VALUES (?, ?, ?)",
                sajuResult.getUserProfile().getId(),
                sajuResult.getUser().getId(),
                sajuResult.getFetchedAt()
        );
    }
}
