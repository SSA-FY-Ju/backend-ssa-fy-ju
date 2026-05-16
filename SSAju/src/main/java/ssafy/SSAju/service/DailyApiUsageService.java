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

        // affectedRows: 1 = INSERT(첫 요청), 2 = UPDATE(증가 성공), 0 = 한도 초과
        if (affectedRows == 0) {
            log.warn("일일 API 사용 한도 초과: userId={}, date={}", userId, today);
            throw new DailyLimitExceededException("하루 3회 분석 제한에 도달했습니다.");
        }
    }
}
