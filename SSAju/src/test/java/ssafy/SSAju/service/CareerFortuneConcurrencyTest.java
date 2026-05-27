package ssafy.SSAju.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.repository.CareerFortuneRepository;
import ssafy.SSAju.repository.HiddenStemDataRepository;
import ssafy.SSAju.repository.SajuFullDataRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.TenGodDataRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * CareerFortuneService 개선 검증 테스트
 *
 * Before (수정 전):
 *   - Race Condition: 20개 동시 요청 → 성공 1, UNIQUE 위반 19
 *   - Connection Pool: 10개 스레드(FastAPI 5s) → 성공 5, 풀 타임아웃 5
 *
 * After (수정 후):
 *   - Race Condition: 20개 동시 요청 → 성공 20, UNIQUE 위반 0
 *   - Connection Pool: 10개 스레드(FastAPI 5s) → 성공 10, 풀 타임아웃 0
 *
 * 설정: HikariCP pool=5, connection-timeout=3000ms
 */
@Slf4j
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.connection-timeout=3000"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("CareerFortuneService 개선 검증 (After Fix)")
class CareerFortuneConcurrencyTest {

    @Autowired private CareerFortuneService service;
    @MockitoBean  private SajuDataService sajuDataService;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private SajuResultRepository sajuResultRepository;
    @Autowired private TenGodDataRepository tenGodDataRepository;
    @Autowired private HiddenStemDataRepository hiddenStemDataRepository;
    @Autowired private CareerFortuneRepository careerFortuneRepository;
    @Autowired private SajuFullDataRepository sajuFullDataRepository;
    @Autowired private UserRepository userRepository;

    private Long testUserId;

    private static final LocalDate BASE_DATE = LocalDate.of(1990, 1, 1);
    private static final LocalTime BASE_TIME = LocalTime.of(12, 0);

    private static final FastAPIResponse MOCK_RESPONSE = new FastAPIResponse(
            List.of("庚", "甲", "己", "丁"),
            List.of("午", "戌", "未", "寅"),
            Map.of("木", 1, "火", 2, "土", 2, "金", 2, "水", 1),
            "庚午", "甲戌", "己未", "丁寅",
            "12:00", "1990-01-01", null
    );

    @BeforeEach
    void cleanDb() {
        // FK 순서에 따라 자식 엔티티부터 삭제
        tenGodDataRepository.deleteAllInBatch();
        hiddenStemDataRepository.deleteAllInBatch();
        careerFortuneRepository.deleteAllInBatch();
        sajuFullDataRepository.deleteAllInBatch();
        sajuResultRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        User testUser = userRepository.save(User.builder()
                .email("concurrency-test@test.com")
                .passwordHash("hash")
                .name("동시성테스트")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(Instant.now())
                .privacyAgreedAt(Instant.now())
                .build());
        testUserId = testUser.getId();
    }

    // ─────────────────────────────────────────────────────────────────
    // Test 1: Race Condition 수정 검증
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("[After] 동일 생년월일시 20개 동시 요청 → 모두 성공, UNIQUE 위반 0")
    void raceConditionFixed_allShouldSucceed() throws InterruptedException {
        // Given: 200ms 지연으로 경쟁 창 극대화 (before 테스트와 동일 조건)
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willAnswer(inv -> {
            Thread.sleep(200);
            return MOCK_RESPONSE;
        });

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        AtomicInteger successCount         = new AtomicInteger(0);
        AtomicInteger uniqueViolationCount = new AtomicInteger(0);
        List<String>  otherErrors          = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    service.analyzeCareerTiming(BASE_DATE, BASE_TIME, testUserId);
                    successCount.incrementAndGet();
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    uniqueViolationCount.incrementAndGet();
                    log.warn("[UNIQUE 위반 - 수정 후에는 발생 불가] {}", rootMessage(e));
                } catch (Exception e) {
                    otherErrors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long startMs = System.currentTimeMillis();
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        long elapsedMs = System.currentTimeMillis() - startMs;

        log.info("============================================================");
        log.info("[Test 1 After] Race Condition 수정 검증");
        log.info("  조건: 스레드 {}, 풀 5, FastAPI 200ms 지연 (before와 동일)", threadCount);
        log.info("  ── Before ──  성공: 1  / UNIQUE 위반: 19");
        log.info("  ── After  ──  성공: {} / UNIQUE 위반: {} / 기타: {}",
                successCount.get(), uniqueViolationCount.get(), otherErrors.size());
        log.info("  전체 소요: {}ms", elapsedMs);
        log.info("  DB UserProfile 수: {}", userProfileRepository.count());
        log.info("  DB SajuResult 수:  {}", sajuResultRepository.count());
        log.info("============================================================");

        assertThat(successCount.get())
                .as("수정 후: 20개 요청 모두 성공해야 함")
                .isEqualTo(threadCount);
        assertThat(uniqueViolationCount.get())
                .as("수정 후: UNIQUE 위반이 caller까지 전파되면 안 됨")
                .isEqualTo(0);
        assertThat(otherErrors).isEmpty();
        assertThat(userProfileRepository.count()).isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────
    // Test 2: 커넥션 풀 고갈 수정 검증
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("[After] FastAPI 5초 지연 × 10 스레드 → 모두 성공, 풀 타임아웃 0")
    void connectionPoolFixed_allShouldSucceed() throws InterruptedException {
        // Given: FastAPI 5s(>pool timeout 3s) — before에서 5개가 timeout 났던 조건
        int fastApiDelayMs = 5_000;
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willAnswer(inv -> {
            Thread.sleep(fastApiDelayMs);
            return MOCK_RESPONSE;
        });

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        AtomicInteger successCount     = new AtomicInteger(0);
        AtomicInteger poolTimeoutCount = new AtomicInteger(0);
        List<Long>    latencies        = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long t0 = System.currentTimeMillis();
                    service.analyzeCareerTiming(BASE_DATE, BASE_TIME.plusMinutes(idx), testUserId);
                    long latency = System.currentTimeMillis() - t0;
                    latencies.add(latency);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    if (isPoolTimeout(e)) {
                        poolTimeoutCount.incrementAndGet();
                        log.warn("  [풀 타임아웃] thread-{}: {}", idx, rootMessage(e));
                    } else {
                        log.error("  [기타 오류] thread-{}: {} - {}", idx,
                                e.getClass().getSimpleName(), e.getMessage());
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long startMs = System.currentTimeMillis();
        startLatch.countDown();
        doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();
        long elapsedMs = System.currentTimeMillis() - startMs;

        LongSummaryStatistics stats = latencies.stream()
                .mapToLong(Long::longValue)
                .summaryStatistics();

        log.info("============================================================");
        log.info("[Test 2 After] 커넥션 풀 고갈 수정 검증");
        log.info("  조건: 스레드 {}, 풀 5, conn-timeout 3000ms, FastAPI {}ms (before와 동일)",
                threadCount, fastApiDelayMs);
        log.info("  ── Before ──  성공: 5  / 풀 타임아웃: 5  / 지연 avg: 5012ms");
        log.info("  ── After  ──  성공: {} / 풀 타임아웃: {} / 지연 avg: {}ms",
                successCount.get(), poolTimeoutCount.get(),
                stats.getCount() > 0 ? (long) stats.getAverage() : -1);
        log.info("  지연 상세(성공): min={}ms max={}ms",
                stats.getCount() > 0 ? stats.getMin() : -1,
                stats.getCount() > 0 ? stats.getMax() : -1);
        log.info("  전체 소요: {}ms", elapsedMs);
        log.info("============================================================");

        assertThat(successCount.get())
                .as("수정 후: 트랜잭션 분리로 10개 요청 모두 성공해야 함")
                .isEqualTo(threadCount);
        assertThat(poolTimeoutCount.get())
                .as("수정 후: FastAPI I/O 중 커넥션 미점유 → 풀 타임아웃 없어야 함")
                .isEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────────

    private boolean isPoolTimeout(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && (
                    msg.contains("Unable to acquire JDBC Connection") ||
                    msg.contains("Connection is not available")       ||
                    msg.contains("Timeout waiting for connection")    ||
                    msg.contains("pool"))) return true;
            cause = cause.getCause();
        }
        return false;
    }

    private String rootMessage(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage();
    }
}
