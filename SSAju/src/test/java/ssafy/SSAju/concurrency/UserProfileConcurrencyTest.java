package ssafy.SSAju.concurrency;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.CareerFortuneRepository;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;
import ssafy.SSAju.repository.SajuFullDataRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserProfileRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동일 생년월일시 조합에 대한 동시 요청이 정확히 1개의 UserProfile만 생성하는지 검증 (US5, T030).
 */
@Testcontainers
@SpringBootTest
@DisplayName("UserProfile 동시 생성 방지 테스트 (US5)")
class UserProfileConcurrencyTest {

    private static final int THREAD_COUNT = 20;

    @Container
    static GenericContainer<?> redis = RedisTestSupport.newRedisContainer();

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        RedisTestSupport.registerRedisProperties(registry, redis);
    }

    @Autowired
    private UserProfileProvider userProfileProvider;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private CareerConsultationRepository careerConsultationRepository;

    @Autowired
    private CareerFortuneRepository careerFortuneRepository;

    @Autowired
    private SajuFullDataRepository sajuFullDataRepository;

    @Autowired
    private SajuResultRepository sajuResultRepository;

    @Autowired
    private CompanyCompatibilityRepository companyCompatibilityRepository;

    @Autowired
    private RedissonClient redissonClient;

    @BeforeEach
    void setUp() {
        // FK 순서: userProfile을 참조하는 자식(및 손자) 테이블을 먼저 비운다.
        careerConsultationRepository.deleteAllInBatch();
        careerFortuneRepository.deleteAllInBatch();
        sajuFullDataRepository.deleteAllInBatch();
        sajuResultRepository.deleteAllInBatch();
        companyCompatibilityRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        redissonClient.getKeys().flushall();
    }

    @Test
    @DisplayName("동일 생년월일시 조합에 대한 N개의 동시 요청은 UserProfile을 정확히 1건만 생성한다")
    void concurrentFindOrCreate_createsExactlyOneUserProfile() throws InterruptedException {
        // Given
        LocalDate birthDate = LocalDate.of(1995, 6, 15);
        LocalTime birthTime = LocalTime.of(8, 30);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Long> resultIds = new CopyOnWriteArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        // When
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    UserProfile result = userProfileProvider.findOrCreate(birthDate, birthTime);
                    resultIds.add(result.getId());
                } catch (Throwable e) {
                    failures.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        boolean finishedInTime;
        try {
            finishedInTime = doneLatch.await(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
        assertThat(finishedInTime).as("30초 내에 모든 스레드가 완료되어야 한다").isTrue();

        // Then
        assertThat(failures).as("동시 요청 중 예외가 발생하지 않아야 한다").isEmpty();
        assertThat(userProfileRepository.count())
                .as("DB에는 UserProfile이 정확히 1건만 존재해야 한다")
                .isEqualTo(1);
        assertThat(resultIds).as("모든 스레드가 동일한 UserProfile.id를 반환해야 한다")
                .hasSize(THREAD_COUNT)
                .containsOnly(resultIds.get(0));
    }
}
