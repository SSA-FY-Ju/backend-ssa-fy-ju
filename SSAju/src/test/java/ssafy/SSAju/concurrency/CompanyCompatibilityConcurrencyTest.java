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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.career.caller.CompanyMatchingOpenAICaller;
import ssafy.SSAju.career.util.ForecastMonthCalculator;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.config.ClockConfig;
import ssafy.SSAju.dto.external.CompatibilityNarrativeResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;
import ssafy.SSAju.repository.DailyApiUsageRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.service.CompanyMatchingService;
import ssafy.SSAju.service.SajuDataService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 동일 (프로필, 회사, 직군) 조합에 대한 동시 요청이 정확히 1개의 CompanyCompatibility만 생성하고,
 * 쿼터도 정확히 1회만 차감되는지 검증 (US5, T031).
 *
 * <p>{@code DailyApiUsageService}가 MySQL의 {@code ON DUPLICATE KEY UPDATE} 문법을 사용하므로
 * MySQL Testcontainers가 필요하다({@code DailyQuotaIntegrityIntegrationTest}와 동일한 이유).
 */
@Testcontainers
@SpringBootTest
@DisplayName("CompanyCompatibility 동시 생성 방지 및 쿼터 단일 차감 테스트 (US5)")
class CompanyCompatibilityConcurrencyTest {

    private static final int THREAD_COUNT = 10;

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.url", () -> mysql.getJdbcUrl() + "?useAffectedRows=true");
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private CompanyMatchingService companyMatchingService;

    @Autowired
    private CompanyCompatibilityRepository companyCompatibilityRepository;

    @Autowired
    private DailyApiUsageRepository dailyApiUsageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ForecastMonthCalculator forecastMonthCalculator;

    @Autowired
    private RedissonClient redissonClient;

    @MockitoBean
    private SajuDataService sajuDataService;

    @MockitoBean
    private CompanyMatchingOpenAICaller companyMatchingOpenAICaller;

    private User testUser;
    private CompatibilityRequest request;

    @BeforeEach
    void setUp() {
        companyCompatibilityRepository.deleteAll();
        dailyApiUsageRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("compatibility-concurrency-test@test.com")
                .passwordHash("hash")
                .name("궁합동시성테스터")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(Instant.now())
                .privacyAgreedAt(Instant.now())
                .build());

        request = new CompatibilityRequest(
                LocalDate.of(1990, 10, 10), null,
                new CompatibilityRequest.TargetRoleRequest(JobCategoryEnum.TECH_BACKEND, null),
                "동시성테스트기업",
                LocalDate.of(2000, 1, 1), null
        );

        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(fakeFastApiResponse());
        given(companyMatchingOpenAICaller.call(any())).willReturn(fakeNarrativeResponse());
    }

    @AfterEach
    void tearDown() {
        redissonClient.getKeys().flushall();
    }

    private FastAPIResponse fakeFastApiResponse() {
        return new FastAPIResponse(
                List.of("庚", "丙", "己", "辛"),
                List.of("午", "戌", "未", "寅"),
                Map.of("木", 1, "火", 2, "土", 1, "金", 2, "水", 2),
                "庚午", "丙戌", "己未", "辛寅",
                "14:30", "1990-10-10", null
        );
    }

    private CompatibilityNarrativeResponse fakeNarrativeResponse() {
        return new CompatibilityNarrativeResponse(
                "요약", "시너지", "경고", "오행 시너지", "약점 방어",
                List.of(new CompatibilityNarrativeResponse.InterviewQuestion("질문", "의도")),
                "전문가 사유", "리드 사유",
                forecastMonthCalculator.currentTargetMonths().stream()
                        .map(month -> new CompatibilityNarrativeResponse.MonthlyAdvice(month, month + "월 조언"))
                        .toList(),
                List.of("주의사항")
        );
    }

    @Test
    @DisplayName("동일 (프로필, 회사, 직군) 조합에 대한 N개 동시 요청은 CompanyCompatibility 1건, 쿼터 1회 차감만 발생시킨다")
    void concurrentAnalyze_createsExactlyOneRowAndDeductsQuotaOnce() throws InterruptedException {
        // Given
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        // When
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    companyMatchingService.analyzeCompatibility(request, testUser.getId());
                } catch (Throwable e) {
                    failures.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        boolean finishedInTime = doneLatch.await(60, TimeUnit.SECONDS);
        assertThat(finishedInTime).as("60초 내에 모든 스레드가 완료되어야 한다").isTrue();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Then
        assertThat(failures).as("동시 요청 중 예외가 발생하지 않아야 한다").isEmpty();
        assertThat(companyCompatibilityRepository.count())
                .as("DB에는 CompanyCompatibility가 정확히 1건만 존재해야 한다")
                .isEqualTo(1);
        assertThat(dailyApiUsageRepository.findByUserIdAndUsageDate(testUser.getId(), LocalDate.now(ClockConfig.SERVICE_ZONE))
                .map(usage -> usage.getRequestCount())
                .orElse(0))
                .as("더블체크락으로 쿼터는 정확히 1회만 차감되어야 한다(이중 차감 방지)")
                .isEqualTo(1);
        verify(sajuDataService, times(2)).fetchSajuFromFastAPI(any(), any()); // 사용자 1회 + 기업 1회, 승자 스레드만
        verify(companyMatchingOpenAICaller, times(1)).call(any());
    }
}
