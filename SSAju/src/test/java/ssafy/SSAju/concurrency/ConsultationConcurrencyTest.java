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
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.service.ConsultationSaveService;

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
 * 동일 (정본, 월) 조합에 대한 동시 요청이 정확히 1개의 CareerConsultation만 생성하는지 검증 (US5, T032).
 */
@Testcontainers
@SpringBootTest
@DisplayName("CareerConsultation 동시 생성 방지 테스트 (US5)")
class ConsultationConcurrencyTest {

    private static final int THREAD_COUNT = 20;
    private static final Integer CONSULTATION_MONTH = 202605;

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private ConsultationSaveService consultationSaveService;

    @Autowired
    private CareerConsultationRepository careerConsultationRepository;

    @Autowired
    private SajuResultRepository sajuResultRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedissonClient redissonClient;

    private SajuResult testSajuResult;

    @BeforeEach
    void setUp() {
        careerConsultationRepository.deleteAllInBatch();
        sajuResultRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        User testUser = userRepository.save(User.builder()
                .email("consultation-concurrency-test@test.com")
                .passwordHash("hash")
                .name("컨설팅동시성테스터")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(Instant.now())
                .privacyAgreedAt(Instant.now())
                .build());

        UserProfile userProfile = userProfileRepository.save(UserProfile.builder()
                .birthDate(LocalDate.of(1990, 7, 15))
                .birthTime(LocalTime.of(12, 0))
                .build());

        testSajuResult = sajuResultRepository.save(SajuResult.builder()
                .userProfile(userProfile)
                .user(testUser)
                .build());
    }

    @AfterEach
    void tearDown() {
        redissonClient.getKeys().flushall();
    }

    @Test
    @DisplayName("동일 (정본, 월) 조합에 대한 N개의 동시 요청은 CareerConsultation을 정확히 1건만 생성한다")
    void concurrentSaveOrUpdate_createsExactlyOneCareerConsultation() throws InterruptedException {
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
                    Long id = consultationSaveService.saveOrUpdate(
                            testSajuResult, fakeAdvice(), "gpt-4o-mini", CONSULTATION_MONTH);
                    resultIds.add(id);
                } catch (Throwable e) {
                    failures.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Then
        assertThat(failures).as("동시 요청 중 예외가 발생하지 않아야 한다").isEmpty();
        assertThat(careerConsultationRepository.count())
                .as("DB에는 CareerConsultation이 정확히 1건만 존재해야 한다")
                .isEqualTo(1);
        assertThat(resultIds).as("모든 스레드가 동일한 CareerConsultation.id를 반환해야 한다")
                .hasSize(THREAD_COUNT)
                .containsOnly(resultIds.get(0));
    }

    private CareerAdviceResponse fakeAdvice() {
        return new CareerAdviceResponse(
                List.of(new CareerAdviceResponse.IndustryRecommendation("IT", "적합", List.of("백엔드 개발자"))),
                List.of("강점을 설명하세요"),
                List.of("분석력", "책임감"),
                List.of(), null, null, null, null, null, null, null, null, null,
                List.of(), null, null
        );
    }
}
