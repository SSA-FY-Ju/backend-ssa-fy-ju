package ssafy.SSAju.concurrency;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.StopWatch;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.repository.CareerFortuneRepository;
import ssafy.SSAju.repository.HiddenStemDataRepository;
import ssafy.SSAju.repository.SajuResultJdbcRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.TenGodDataRepository;
import ssafy.SSAju.repository.UserProfileRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA save() + DataIntegrityViolationException catch 방식 vs
 * JdbcTemplate INSERT IGNORE 방식의 동시성 성능 비교 테스트
 *
 * 100개 스레드가 동일한 UserProfile (unique key: birth_date + birth_time)에 대해
 * SajuResult를 동시에 저장할 때 각 방식의 소요 시간·예외 횟수·정합성을 검증한다.
 */
@Slf4j
@SpringBootTest
@DisplayName("JPA save() vs JDBC INSERT IGNORE 동시성 성능 비교")
class JpaVsJdbcConcurrencyBenchmarkTest {

    private static final int THREAD_COUNT = 100;
    private static final int AWAIT_SECONDS = 30;

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 7, 15);
    private static final LocalTime BIRTH_TIME = LocalTime.of(12, 0);

    private static final Map<String, Object> FULL_SAJU_DATA = Map.of(
            "heavenlyStems",    List.of("庚", "甲", "己", "丁"),
            "earthlyBranches",  List.of("午", "戌", "未", "寅"),
            "fiveElements",     Map.of("木", 1, "火", 2, "土", 2, "金", 2, "水", 1)
    );

    @Autowired private SajuResultRepository      sajuResultRepository;
    @Autowired private SajuResultJdbcRepository  sajuResultJdbcRepository;
    @Autowired private UserProfileRepository     userProfileRepository;
    @Autowired private TenGodDataRepository      tenGodDataRepository;
    @Autowired private HiddenStemDataRepository  hiddenStemDataRepository;
    @Autowired private CareerFortuneRepository   careerFortuneRepository;

    @BeforeEach
    void cleanDb() {
        tenGodDataRepository.deleteAllInBatch();
        hiddenStemDataRepository.deleteAllInBatch();
        careerFortuneRepository.deleteAllInBatch();
        sajuResultRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("100 스레드 동시 INSERT - JPA(예외 처리) vs JDBC(INSERT IGNORE) 비교")
    void compare_jpa_vs_jdbc_concurrent_insert() throws InterruptedException {

        // 두 케이스 공용 UserProfile (unique key: birth_date + birth_time)
        UserProfile sharedProfile = userProfileRepository.save(
                UserProfile.builder()
                        .birthDate(BIRTH_DATE)
                        .birthTime(BIRTH_TIME)
                        .build()
        );

        StopWatch stopWatch = new StopWatch("JPA-vs-JDBC");

        // ═══════════════════════════════════════════════════════════
        // Case A : JPA save() + DataIntegrityViolationException catch
        // ═══════════════════════════════════════════════════════════

        AtomicInteger jpaExceptionCount = new AtomicInteger(0);
        AtomicInteger jpaSuccessCount   = new AtomicInteger(0);

        CountDownLatch startLatchA = new CountDownLatch(1);  // 스레드 일제 출발 신호
        CountDownLatch doneLatchA  = new CountDownLatch(THREAD_COUNT);
        ExecutorService executorA  = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executorA.submit(() -> {
                try {
                    startLatchA.await();  // 모든 스레드가 준비될 때까지 대기

                    SajuResult result = SajuResult.builder()
                            .userProfile(sharedProfile)
                            .fullSajuData(FULL_SAJU_DATA)
                            .build();
                    sajuResultRepository.save(result);
                    jpaSuccessCount.incrementAndGet();

                } catch (DataIntegrityViolationException e) {
                    // 중복 키 제약 위반 → 예상된 예외
                    jpaExceptionCount.incrementAndGet();
                } catch (Exception e) {
                    // 예상치 못한 예외
                    jpaExceptionCount.incrementAndGet();
                    log.warn("[JPA] 예상치 못한 예외: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                } finally {
                    doneLatchA.countDown();
                }
            });
        }

        stopWatch.start("JPA");
        startLatchA.countDown();                          // 일제 출발
        doneLatchA.await(AWAIT_SECONDS, TimeUnit.SECONDS);
        stopWatch.stop();

        executorA.shutdown();
        executorA.awaitTermination(5, TimeUnit.SECONDS);

        long jpaMs      = stopWatch.getLastTaskTimeMillis();
        long jpaDbCount = sajuResultRepository.count();

        // Case A 정합성 검증
        assertThat(jpaDbCount)
                .as("JPA: 동시 INSERT 후 DB에 SajuResult는 정확히 1건이어야 한다")
                .isEqualTo(1);
        assertThat(jpaSuccessCount.get() + jpaExceptionCount.get())
                .as("JPA: 성공 + 예외 합계는 전체 스레드 수와 같아야 한다")
                .isEqualTo(THREAD_COUNT);

        // Case A와 B 사이 데이터 정리 (UserProfile은 유지)
        sajuResultRepository.deleteAllInBatch();

        // ═══════════════════════════════════════════════════════════
        // Case B : JdbcTemplate INSERT IGNORE
        // ═══════════════════════════════════════════════════════════

        AtomicInteger jdbcExceptionCount = new AtomicInteger(0);
        AtomicInteger jdbcSuccessCount   = new AtomicInteger(0);

        CountDownLatch startLatchB = new CountDownLatch(1);
        CountDownLatch doneLatchB  = new CountDownLatch(THREAD_COUNT);
        ExecutorService executorB  = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executorB.submit(() -> {
                try {
                    startLatchB.await();

                    SajuResult result = SajuResult.builder()
                            .userProfile(sharedProfile)
                            .fullSajuData(FULL_SAJU_DATA)
                            .build();
                    sajuResultJdbcRepository.insertOrIgnore(result);
                    jdbcSuccessCount.incrementAndGet();

                } catch (Exception e) {
                    jdbcExceptionCount.incrementAndGet();
                    log.warn("[JDBC] 예외: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                } finally {
                    doneLatchB.countDown();
                }
            });
        }

        stopWatch.start("JDBC");
        startLatchB.countDown();
        doneLatchB.await(AWAIT_SECONDS, TimeUnit.SECONDS);
        stopWatch.stop();

        executorB.shutdown();
        executorB.awaitTermination(5, TimeUnit.SECONDS);

        long jdbcMs      = stopWatch.getLastTaskTimeMillis();
        long jdbcDbCount = sajuResultRepository.count();

        // Case B 정합성 검증
        assertThat(jdbcDbCount)
                .as("JDBC: 동시 INSERT IGNORE 후 DB에 SajuResult는 정확히 1건이어야 한다")
                .isEqualTo(1);
        assertThat(jdbcExceptionCount.get())
                .as("JDBC: INSERT IGNORE 방식에서는 예외가 발생하지 않아야 한다")
                .isZero();
        assertThat(jdbcSuccessCount.get())
                .as("JDBC: 모든 스레드가 예외 없이 완료되어야 한다")
                .isEqualTo(THREAD_COUNT);

        // ═══════════════════════════════════════════════════════════
        // 최종 비교 리포트
        // ═══════════════════════════════════════════════════════════
        long   diffMs       = jpaMs - jdbcMs;
        double improvePct   = jpaMs > 0 ? (double) diffMs / jpaMs * 100 : 0;
        String improvement  = String.format("%.1f%%", improvePct);

        log.info("╔══════════════════════════════════════════════════════╗");
        log.info("║   동시성 성능 비교 결과  (스레드 {}개)                 ║", THREAD_COUNT);
        log.info("╠══════════════════════════════════════════════════════╣");
        log.info("║  [Case A] JPA save() + DataIntegrityViolationException");
        log.info("║    소요 시간    : {} ms", jpaMs);
        log.info("║    예외 발생    : {} 건", jpaExceptionCount.get());
        log.info("║    정상 처리    : {} 건", jpaSuccessCount.get());
        log.info("║    DB 최종 행수 : {} 건", jpaDbCount);
        log.info("╠══════════════════════════════════════════════════════╣");
        log.info("║  [Case B] JdbcTemplate INSERT IGNORE");
        log.info("║    소요 시간    : {} ms", jdbcMs);
        log.info("║    예외 발생    : {} 건", jdbcExceptionCount.get());
        log.info("║    정상 처리    : {} 건", jdbcSuccessCount.get());
        log.info("║    DB 최종 행수 : {} 건", jdbcDbCount);
        log.info("╠══════════════════════════════════════════════════════╣");
        log.info("  >>> JPA 소요시간: {} ms  vs  JDBC 소요시간: {} ms  (차이 {} ms, {} 개선)",
                jpaMs, jdbcMs, diffMs, improvement);
        log.info("╚══════════════════════════════════════════════════════╝");
        log.info(stopWatch.prettyPrint());
    }
}
