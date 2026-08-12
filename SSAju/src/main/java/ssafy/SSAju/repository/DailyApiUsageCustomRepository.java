package ssafy.SSAju.repository;

import java.time.LocalDate;

public interface DailyApiUsageCustomRepository {

    /**
     * INSERT ... ON DUPLICATE KEY UPDATE 방식으로 단일 쿼리에서 Upsert 처리.
     * @return 1 = INSERT(첫 요청), 2 = UPDATE(증가 성공), 0 = 한도 초과(변경 없음)
     */
    int upsertUsageIfUnderLimit(Long userId, LocalDate date, int limit);

    /**
     * 외부 API 호출 실패 시 차감된 쿼터를 보상(복원)합니다. 0 미만으로는 내려가지 않습니다.
     * @return 갱신된 행 수 (0 = 해당 유저/날짜의 사용 기록 없음)
     */
    int restoreUsage(Long userId, LocalDate date);
}
