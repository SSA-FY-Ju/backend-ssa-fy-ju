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
import ssafy.SSAju.service.DailyApiUsageService;
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
 * 동일 (프로필, 회사, 직군) 조합에 대한 동시 요청이 DB에는 정확히 1개의 CompanyCompatibility만
 * 남기는지 검증한다(US5, T031 후속 리팩토링).
 *
 * <p>락 배치를 저장 단계로 좁히면서(외부 I/O는 락 밖) 더블체크 캐시 확인을 제거했다 — 완전히
 * 동일한 요청이 동시에 오면 FastAPI/OpenAI가 스레드 수만큼 중복 호출되는 것은 감수한다(단,
 * 최종 저장은 락 덕분에 항상 1건으로 수렴한다). 다만 쿼터는 saveWithLock의 결과(newlyCreated)를
 * 보고 경합에서 진 요청만 보상 복원하므로, 동시 요청 수와 무관하게 실제로 새 행을 만든 1건만
 * 최종 차감으로 남는다. 그래도 각 스레드의 최초 차감(checkAndIncrementDailyUsage)은 보상 이전에
 * 동시에 일어나므로, 스레드 수를 일일 쿼터 한도({@code DailyApiUsageService.DAILY_REQUEST_LIMIT=3})
 * 이내로 제한해 한도 초과로 인한 {@code DailyLimitExceededException}이 "예외 없음" 검증을
 * 방해하지 않게 한다.
 *
 * <p>{@code DailyApiUsageService}가 MySQL의 {@code ON DUPLICATE KEY UPDATE} 문법을 사용하므로
 * MySQL Testcontainers가 필요하다({@code DailyQuotaIntegrityIntegrationTest}와 동일한 이유).
 */
@Testcontainers
@SpringBootTest
@DisplayName("CompanyCompatibility 동시 생성 방지 테스트 (US5)")
class CompanyCompatibilityConcurrencyTest {

    /** 일일 쿼터 한도를 넘지 않는 선에서 동시 요청을 재현한다. */
    private static final int THREAD_COUNT = DailyApiUsageService.DAILY_REQUEST_LIMIT;

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Container
    static GenericContainer<?> redis = RedisTestSupport.newRedisContainer();

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.url", () -> mysql.getJdbcUrl() + "?useAffectedRows=true");
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        RedisTestSupport.registerRedisProperties(registry, redis);
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
    @DisplayName("동일 (프로필, 회사, 직군) 조합에 대한 N개 동시 요청도 CompanyCompatibility는 정확히 1건만 남는다")
    void concurrentAnalyze_createsExactlyOneRow() throws InterruptedException {
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
        boolean finishedInTime;
        try {
            finishedInTime = doneLatch.await(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Executor did not terminate after shutdownNow()");
                }
            }
        }
        assertThat(finishedInTime).as("60초 내에 모든 스레드가 완료되어야 한다").isTrue();

        // Then
        assertThat(failures).as("동시 요청 중 예외가 발생하지 않아야 한다").isEmpty();
        assertThat(companyCompatibilityRepository.count())
                .as("동시에 완전히 동일한 요청이 와도 DB에는 CompanyCompatibility가 정확히 1건만 남아야 한다")
                .isEqualTo(1);
        // 더블체크락을 제거했으므로 각 스레드가 FastAPI/OpenAI를 각자 호출한다 — 그건 감수한
        // 트레이드오프다. 다만 쿼터는 다르다: saveWithLock이 남의 행을 재사용(newlyCreated=false)
        // 하면 CompanyMatchingService가 해당 스레드의 쿼터를 보상 복원하므로, 동시에 완전히
        // 동일한 요청이 THREAD_COUNT개 와도 최종적으로 남는 차감은 실제로 새 행을 만든 1건뿐이다.
        assertThat(dailyApiUsageRepository.findByUserIdAndUsageDate(testUser.getId(), LocalDate.now(ClockConfig.SERVICE_ZONE))
                .map(usage -> usage.getRequestCount())
                .orElse(0))
                .as("경합에서 진 요청은 쿼터가 보상 복원되므로, 실제로 새 행을 만든 요청 1건만 남는다")
                .isEqualTo(1);
        verify(sajuDataService, times(THREAD_COUNT * 2)).fetchSajuFromFastAPI(any(), any()); // 스레드마다 사용자 1회 + 기업 1회
        verify(companyMatchingOpenAICaller, times(THREAD_COUNT)).call(any());
    }
}
