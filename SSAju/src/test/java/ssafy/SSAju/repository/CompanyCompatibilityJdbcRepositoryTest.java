package ssafy.SSAju.repository;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.util.JobCategoryEnum;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@DisplayName("CompanyCompatibilityJdbcRepository INSERT IGNORE 테스트")
class CompanyCompatibilityJdbcRepositoryTest {

    @Autowired private CompanyCompatibilityJdbcRepository jdbcRepository;
    @Autowired private CompanyCompatibilityRepository compatibilityRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private SajuResultRepository sajuResultRepository;
    @Autowired private TenGodDataRepository tenGodDataRepository;
    @Autowired private HiddenStemDataRepository hiddenStemDataRepository;
    @Autowired private CareerFortuneRepository careerFortuneRepository;
    @Autowired private SajuFullDataRepository sajuFullDataRepository;

    private UserProfile savedProfile;

    @BeforeEach
    void setUp() {
        // 외래키 의존성 역순 삭제
        compatibilityRepository.deleteAllInBatch();
        tenGodDataRepository.deleteAllInBatch();
        hiddenStemDataRepository.deleteAllInBatch();
        careerFortuneRepository.deleteAllInBatch();
        sajuFullDataRepository.deleteAllInBatch();
        sajuResultRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();

        savedProfile = userProfileRepository.save(
                UserProfile.builder()
                        .birthDate(LocalDate.of(1998, 5, 7))
                        .birthTime(LocalTime.of(14, 30))
                        .build()
        );
    }

    @Test
    @DisplayName("신규 삽입 → insertOrIgnore 반환값 1")
    void shouldReturn1_WhenNewInsert() {
        // Given
        CompanyCompatibility entity = buildEntity(savedProfile, "현대오토에버", JobCategoryEnum.TECH_BACKEND);

        // When
        int result = jdbcRepository.insertOrIgnore(entity);

        // Then
        assertThat(result).isEqualTo(1);
        assertThat(compatibilityRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("중복 삽입 → insertOrIgnore 반환값 0 (IGNORE)")
    void shouldReturn0_WhenDuplicateInsert() {
        // Given: 동일 조합 먼저 삽입
        CompanyCompatibility first = buildEntity(savedProfile, "현대오토에버", JobCategoryEnum.TECH_BACKEND);
        jdbcRepository.insertOrIgnore(first);

        CompanyCompatibility duplicate = buildEntity(savedProfile, "현대오토에버", JobCategoryEnum.TECH_BACKEND);

        // When
        int result = jdbcRepository.insertOrIgnore(duplicate);

        // Then
        assertThat(result).isEqualTo(0);
        assertThat(compatibilityRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("N개 동시 요청 → DB에 1건만 존재, 예외 없음 (Race Condition)")
    void shouldInsertOnlyOne_WhenConcurrentRequests() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    CompanyCompatibility entity =
                            buildEntity(savedProfile, "삼성전자", JobCategoryEnum.TECH_DATA);
                    jdbcRepository.insertOrIgnore(entity);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        boolean completed;
        try {
            startLatch.countDown();
            completed = doneLatch.await(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        log.info("동시성 테스트: 성공={}, 예외={}, DB 건수={}",
                successCount.get(), errors.size(), compatibilityRepository.count());

        assertThat(completed).as("15초 내 완료").isTrue();
        assertThat(errors).as("예외 없어야 함").isEmpty();
        assertThat(successCount.get()).as("모든 스레드 성공").isEqualTo(threadCount);
        assertThat(compatibilityRepository.count()).as("DB에 1건만 존재").isEqualTo(1);
    }

    private CompanyCompatibility buildEntity(UserProfile profile, String companyName,
                                              JobCategoryEnum category) {
        return CompanyCompatibility.builder()
                .userProfile(profile)
                .companyName(companyName)
                .targetRoleCategory(category)
                .targetRoleDetailName("테스트 직무")
                .compatibilityScore(75)
                .summary("테스트 요약")
                .build();
    }
}
