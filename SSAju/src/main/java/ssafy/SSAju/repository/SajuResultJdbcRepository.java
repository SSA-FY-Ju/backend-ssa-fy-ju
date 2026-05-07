package ssafy.SSAju.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.exception.DataAccessException;
import tools.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
public class SajuResultJdbcRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public int insertOrIgnore(SajuResult sajuResult) {
        String fullSajuDataJson = serializeFullSajuData(sajuResult);
        return jdbcTemplate.update(
                "INSERT IGNORE INTO saju_result (user_profile_id, full_saju_data, fetched_at) VALUES (?, ?, ?)",
                sajuResult.getUserProfile().getId(),
                fullSajuDataJson,
                sajuResult.getFetchedAt()
        );
    }

    private String serializeFullSajuData(SajuResult sajuResult) {
        if (sajuResult.getFullSajuData() == null) return null;
        try {
            return objectMapper.writeValueAsString(sajuResult.getFullSajuData());
        } catch (Exception e) {
            throw new DataAccessException("full_saju_data JSON 직렬화 실패", e);
        }
    }
}
