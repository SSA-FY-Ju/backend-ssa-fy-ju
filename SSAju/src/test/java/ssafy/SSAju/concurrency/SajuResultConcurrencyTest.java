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
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.provider.SajuResultProvider;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.CareerFortuneRepository;
import ssafy.SSAju.repository.SajuFullDataRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.Instant;
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
 * 동일 (user, userProfile) 조합에 대한 동시 요청이 정확히 1개의 SajuResult만 생성하는지 검증 (US5, T029).
 */
@Testcontainers
@SpringBootTest
@DisplayName("SajuResult 동시 생성 방지 테스트 (US5)")
class SajuResultConcurrencyTest {

    private static final int THREAD_COUNT = 20;

    @Container
    static GenericContainer<?> redis = RedisTestSupport.newRedisContainer();

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        RedisTestSupport.registerRedisProperties(registry, redis);
    }

    @Autowired
    private SajuResultProvider sajuResultProvider;

    @Autowired
    private SajuResultRepository sajuResultRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private CareerFortuneRepository careerFortuneRepository;

    @Autowired
    private SajuFullDataRepository sajuFullDataRepository;

    @Autowired
    private CareerConsultationRepository careerConsultationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedissonClient redissonClient;

    private User testUser;
    private UserProfile testUserProfile;

    @BeforeEach
    void setUp() {
        careerConsultationRepository.deleteAllInBatch();
        careerFortuneRepository.deleteAllInBatch();
        sajuFullDataRepository.deleteAllInBatch();
        sajuResultRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        testUser = userRepository.save(User.builder()
                .email("saju-concurrency-test@test.com")
                .passwordHash("hash")
                .name("사주동시성테스터")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(Instant.now())
                .privacyAgreedAt(Instant.now())
                .build());

        testUserProfile = userProfileRepository.save(UserProfile.builder()
                .birthDate(LocalDate.of(1990, 7, 15))
                .birthTime(LocalTime.of(12, 0))
                .build());
    }

    @AfterEach
    void tearDown() {
        redissonClient.getKeys().flushall();
    }

    @Test
    @DisplayName("동일 userProfile에 대한 N개의 동시 요청은 SajuResult를 정확히 1건만 생성한다")
    void concurrentFindOrCreate_createsExactlyOneSajuResult() throws InterruptedException {
        // Given
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
                    SajuResult newResult = SajuResult.builder()
                            .userProfile(testUserProfile)
                            .user(testUser)
                            .build();
                    SajuResult result = sajuResultProvider.findOrCreate(testUser, testUserProfile, newResult);
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
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Executor did not terminate after shutdownNow()");
                }
            }
        }
        assertThat(finishedInTime).as("30초 내에 모든 스레드가 완료되어야 한다").isTrue();

        // Then
        assertThat(failures).as("동시 요청 중 예외가 발생하지 않아야 한다").isEmpty();
        assertThat(sajuResultRepository.count())
                .as("DB에는 SajuResult가 정확히 1건만 존재해야 한다")
                .isEqualTo(1);
        assertThat(resultIds).as("모든 스레드가 동일한 SajuResult.id를 반환해야 한다")
                .hasSize(THREAD_COUNT)
                .containsOnly(resultIds.get(0));
    }
}
