package ssafy.SSAju.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.SSAju.exception.DailyLimitExceededException;
import ssafy.SSAju.repository.DailyApiUsageRepository;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Service
public class DailyApiUsageService {

    private static final int DAILY_REQUEST_LIMIT = 3;
    private static final String KST_ZONE = "Asia/Seoul";

    private final DailyApiUsageRepository dailyApiUsageRepository;

    public DailyApiUsageService(DailyApiUsageRepository dailyApiUsageRepository) {
        this.dailyApiUsageRepository = dailyApiUsageRepository;
    }

    public void checkAndIncrementDailyUsage(Long userId) {
        LocalDate today = LocalDate.now(ZoneId.of(KST_ZONE));

        int affectedRows = dailyApiUsageRepository.upsertUsageIfUnderLimit(userId, today, DAILY_REQUEST_LIMIT);

        // useAffectedRows=true 기준 (application.yaml DB_URL 및 테스트 Testcontainers URL 필수 설정)
        // 1 = INSERT(첫 요청 성공), 2 = UPDATE(증가 성공), 0 = no-op(한도 초과) → 예외 발생
        if (affectedRows == 0) {
            log.warn("일일 API 사용 한도 초과: userId={}, date={}", userId, today);
            throw new DailyLimitExceededException("하루 3회 분석 제한에 도달했습니다.");
        }
    }

    /**
     * 외부 API(FastAPI/공공데이터/OpenAI) 호출 실패 시 차감된 쿼터를 보상(복원)합니다.
     * 차감과 별개의 트랜잭션으로 실행되는 보상 트랜잭션 방식이므로, 분산락 도입 여부와 무관하게 안전합니다.
     */
    public void restoreDailyUsage(Long userId) {
        LocalDate today = LocalDate.now(ZoneId.of(KST_ZONE));

        int affectedRows = dailyApiUsageRepository.restoreUsage(userId, today);
        if (affectedRows == 0) {
            log.warn("쿼터 복원 대상 없음: userId={}, date={}", userId, today);
        }
    }
}
