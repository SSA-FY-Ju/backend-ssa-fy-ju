package ssafy.SSAju.repository;

import java.time.LocalDate;

public interface DailyApiUsageCustomRepository {

    /**
     * INSERT ... ON DUPLICATE KEY UPDATE 방식으로 단일 쿼리에서 Upsert 처리.
     * @return 1 = INSERT(첫 요청), 2 = UPDATE(증가 성공), 0 = 한도 초과(변경 없음)
     */
    int upsertUsageIfUnderLimit(Long userId, LocalDate date, int limit);
}
