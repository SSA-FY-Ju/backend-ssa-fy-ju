package ssafy.SSAju.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ssafy.SSAju.entity.DailyApiUsage;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.DailyLimitExceededException;
import ssafy.SSAju.repository.DailyApiUsageRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class DailyApiUsageService {

    private static final Logger log = LoggerFactory.getLogger(DailyApiUsageService.class);
    private static final int DAILY_REQUEST_LIMIT = 3;
    private static final String KST_ZONE = "Asia/Seoul";

    private final JdbcTemplate jdbcTemplate;
    private final DailyApiUsageRepository dailyApiUsageRepository;
    private final UserRepository userRepository;

    public DailyApiUsageService(JdbcTemplate jdbcTemplate,
                                DailyApiUsageRepository dailyApiUsageRepository,
                                UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.dailyApiUsageRepository = dailyApiUsageRepository;
        this.userRepository = userRepository;
    }

    public void checkAndIncrementDailyUsage(Long userId) {
        LocalDate today = LocalDate.now(ZoneId.of(KST_ZONE));

        // Atomic UPDATE: request_count < 3 조건에서만 증가 (Race Condition 안전)
        String sql = """
                UPDATE daily_api_usage
                SET request_count = request_count + 1
                WHERE user_id = ?
                  AND usage_date = ?
                  AND request_count < ?
                """;

        int updatedRows = jdbcTemplate.update(sql, userId, today, DAILY_REQUEST_LIMIT);

        if (updatedRows > 0) {
            return; // 정상 처리 완료
        }

        // updateCount == 0: 오늘 기록 없거나 한도 초과
        dailyApiUsageRepository.findByUserIdAndUsageDate(userId, today).ifPresentOrElse(
                usage -> {
                    if (usage.getRequestCount() >= DAILY_REQUEST_LIMIT) {
                        throw new DailyLimitExceededException("하루 3회 분석 제한에 도달했습니다.");
                    }
                },
                () -> createFirstUsageRecord(userId, today)
        );
    }

    private void createFirstUsageRecord(Long userId, LocalDate today) {
        try {
            User user = userRepository.getReferenceById(userId);
            DailyApiUsage usage = DailyApiUsage.builder()
                    .user(user)
                    .requestCount(1)
                    .usageDate(today)
                    .build();
            dailyApiUsageRepository.save(usage);
        } catch (DataIntegrityViolationException e) {
            // Race Condition: 동시 요청으로 다른 스레드가 먼저 INSERT한 경우
            // UNIQUE 제약 위반 → 재조회하여 한도 확인
            log.warn("DailyApiUsage Race Condition 감지: userId={}", userId);
            dailyApiUsageRepository.findByUserIdAndUsageDate(userId, today).ifPresent(usage -> {
                if (usage.getRequestCount() >= DAILY_REQUEST_LIMIT) {
                    throw new DailyLimitExceededException("하루 3회 분석 제한에 도달했습니다.");
                }
            });
        }
    }
}
