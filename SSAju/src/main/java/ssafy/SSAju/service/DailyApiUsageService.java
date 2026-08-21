package ssafy.SSAju.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.SSAju.exception.DailyLimitExceededException;
import ssafy.SSAju.repository.DailyApiUsageRepository;

import java.time.Clock;
import java.time.LocalDate;

@Slf4j
@Service
public class DailyApiUsageService {

    public static final int DAILY_REQUEST_LIMIT = 3;

    private final DailyApiUsageRepository dailyApiUsageRepository;
    private final Clock clock;

    public DailyApiUsageService(DailyApiUsageRepository dailyApiUsageRepository, Clock clock) {
        this.dailyApiUsageRepository = dailyApiUsageRepository;
        this.clock = clock;
    }

    public LocalDate checkAndIncrementDailyUsage(Long userId) {
        LocalDate today = LocalDate.now(clock);

        int affectedRows = dailyApiUsageRepository.upsertUsageIfUnderLimit(userId, today, DAILY_REQUEST_LIMIT);

        // useAffectedRows=true 기준 (application.yaml DB_URL 및 테스트 Testcontainers URL 필수 설정)
        // 1 = INSERT(첫 요청 성공), 2 = UPDATE(증가 성공), 0 = no-op(한도 초과) → 예외 발생
        if (affectedRows == 0) {
            log.warn("일일 API 사용 한도 초과: userId={}, date={}", userId, today);
            throw new DailyLimitExceededException("하루 3회 분석 제한에 도달했습니다.");
        }
        return today;
    }

    /**
     * 외부 API(FastAPI/공공데이터/OpenAI) 호출 실패 시 차감된 쿼터를 보상(복원)합니다.
     * 차감과 별개의 트랜잭션으로 실행되는 보상 트랜잭션 방식이므로, 분산락 도입 여부와 무관하게 안전합니다.
     *
     * <p>날짜는 {@link #checkAndIncrementDailyUsage}가 반환한 값을 그대로 받아야 한다. 복원 시점에
     * 현재 날짜를 다시 계산하면, 차감과 복원 사이(외부 API 호출 대기 등)에 자정을 넘길 경우 서로 다른
     * 날짜 레코드를 건드리게 되어 원래 차감된 날짜의 쿼터가 영구히 소진 상태로 남는다.
     */
    public void restoreDailyUsage(Long userId, LocalDate usageDate) {
        int affectedRows = dailyApiUsageRepository.restoreUsage(userId, usageDate);
        if (affectedRows == 0) {
            log.warn("쿼터 복원 대상 없음: userId={}, date={}", userId, usageDate);
        }
    }

    /**
     * {@link #restoreDailyUsage}를 호출하되, 복원 자체가 실패해도(예: 일시적 DB 오류) 예외를
     * 밖으로 던지지 않고 로그만 남긴다 — 보상 트랜잭션은 "최선을 다해 되돌려주는" 성격이라,
     * 복원 실패로 원래 처리 흐름(호출자가 던지려던 예외 등)을 가로막으면 안 되기 때문이다.
     *
     * @param suppressInto 복원 실패를 알리는 예외를 붙여 넣을 대상(호출자가 곧 다시 던질 원본
     *                      예외). 복원할 활성 예외가 없는 호출(예: 락 경합으로 인한 정상 반환
     *                      경로)이면 null을 넘긴다.
     */
    public void restoreQuietly(Long userId, LocalDate usageDate, RuntimeException suppressInto) {
        try {
            restoreDailyUsage(userId, usageDate);
        } catch (RuntimeException restoreException) {
            log.error("쿼터 복원 실패 (userId={}, usageDate={})", userId, usageDate, restoreException);
            if (suppressInto != null) {
                suppressInto.addSuppressed(restoreException);
            }
        }
    }

    public void restoreQuietly(Long userId, LocalDate usageDate) {
        restoreQuietly(userId, usageDate, null);
    }
}
