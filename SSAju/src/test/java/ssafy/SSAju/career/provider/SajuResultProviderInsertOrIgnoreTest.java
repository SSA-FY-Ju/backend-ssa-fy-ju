package ssafy.SSAju.career.provider;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.repository.CareerFortuneRepository;
import ssafy.SSAju.repository.HiddenStemDataRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.TenGodDataRepository;
import ssafy.SSAju.repository.UserProfileRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@DisplayName("SajuResultProvider INSERT IGNORE 동시성 테스트")
class SajuResultProviderInsertOrIgnoreTest {

    @Autowired private SajuResultProvider sajuResultProvider;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private SajuResultRepository sajuResultRepository;
    @Autowired private TenGodDataRepository tenGodDataRepository;
    @Autowired private HiddenStemDataRepository hiddenStemDataRepository;
    @Autowired private CareerFortuneRepository careerFortuneRepository;

    private static final Map<String, Object> FULL_SAJU_DATA = Map.of(
            "heavenlyStems", List.of("庚", "甲", "己", "丁"),
            "earthlyBranches", List.of("午", "戌", "未", "寅"),
            "fiveElements", Map.of("木", 1, "火", 2, "土", 2, "金", 2, "水", 1)
    );

    @BeforeEach
    void cleanDb() {
        tenGodDataRepository.deleteAllInBatch();
        hiddenStemDataRepository.deleteAllInBatch();
        careerFortuneRepository.deleteAllInBatch();
        sajuResultRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
    }

    // ─────────────────────────────────────────────────────────────────
    // INSERT IGNORE 동시성 검증: N개 스레드 → DB에 1건만 존재
    // ─────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "스레드 {0}개 동시 INSERT IGNORE")
    @ValueSource(ints = {5, 10, 20})
    @DisplayName("동일 UserProfile에 N개 동시 요청 → SajuResult 1건, 예외 0")
    void insertOrIgnore_concurrentSameUserProfile_onlyOneRowInserted(int threadCount)
            throws InterruptedException {

        LocalDate birthDate = LocalDate.of(1995, 6, 15);
        LocalTime birthTime = LocalTime.of(8, 30);

        UserProfile userProfile = userProfileRepository.save(
                UserProfile.builder().birthDate(birthDate).birthTime(birthTime).build());

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        AtomicInteger successCount   = new AtomicInteger(0);
        List<String>  errors         = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    SajuResult newResult = SajuResult.builder()
                            .userProfile(userProfile)
                            .fullSajuData(FULL_SAJU_DATA)
                            .build();
                    sajuResultProvider.findOrCreate(userProfile, newResult);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        long savedCount = sajuResultRepository.count();

        log.info("────────────────────────────────────────");
        log.info("[INSERT IGNORE 동시성] 스레드 수: {}", threadCount);
        log.info("  성공: {} / 예외: {} / DB SajuResult: {}", successCount.get(), errors.size(), savedCount);
        if (!errors.isEmpty()) log.warn("  예외 목록: {}", errors);
        log.info("────────────────────────────────────────");

        assertThat(successCount.get()).as("모든 스레드가 성공해야 함").isEqualTo(threadCount);
        assertThat(errors).as("예외 없어야 함").isEmpty();
        assertThat(savedCount).as("DB에 SajuResult 1건만 존재").isEqualTo(1);
    }
}
