package ssafy.SSAju.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.career.caller.CompanyMatchingOpenAICaller;
import ssafy.SSAju.career.caller.ConsultationOpenAICaller;
import ssafy.SSAju.career.domain.AnalysisTimeContext;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.config.ClockConfig;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.CompatibilityNarrativeResponse;
import ssafy.SSAju.dto.request.CareerTimingRequest;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.dto.request.ConsultationRequest;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.DailyLimitExceededException;
import ssafy.SSAju.exception.ExternalApiException;
import ssafy.SSAju.repository.DailyApiUsageRepository;
import ssafy.SSAju.repository.ManseRepository;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.service.AnalysisService;
import ssafy.SSAju.service.SajuDataService;

import java.time.Clock;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 컨설팅·궁합이 공유하는 하루 3회 한도와 원래 날짜 보상을 실제 MySQL에서 검증한다(T041).
 *
 * <p>한도 판정은 조건부 upsert가 돌려주는 "실제로 바뀐 행 수"에 기대므로 MySQL이 필요하다.
 * {@code useAffectedRows}는 URL 옵션이 아니라 운영과 같은 공통 Hikari 설정으로 전달한다.
 */
@Testcontainers
@SpringBootTest
@DisplayName("일일 쿼터 무결성 통합 테스트")
class DailyQuotaIntegrityIntegrationTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);
    private static final int DAILY_LIMIT = 3;

    @Container
    static MySQLContainer<?> mysql = UnifiedAnalysisTestSupport.newMySqlContainer();

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        UnifiedAnalysisTestSupport.registerMySqlProperties(registry, mysql);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @MockitoBean private SajuDataService sajuDataService;
    @MockitoBean private ConsultationOpenAICaller consultationOpenAICaller;
    @MockitoBean private CompanyMatchingOpenAICaller companyMatchingOpenAICaller;

    @Autowired private AnalysisService analysisService;
    @Autowired private DailyApiUsageRepository dailyApiUsageRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SajuRepository sajuRepository;
    @Autowired private ManseRepository manseRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private Clock clock;

    private User testUser;

    @BeforeEach
    void setUp() {
        dailyApiUsageRepository.deleteAll();
        sajuRepository.deleteAllInBatch();
        manseRepository.deleteAllInBatch();
        userRepository.deleteAll();
        userProfileRepository.deleteAllInBatch();

        testUser = userRepository.save(UnifiedAnalysisTestSupport.newUser("quota-test@test.com"));

        ManseData fixture = UnifiedAnalysisTestSupport.manseData();
        given(sajuDataService.fetchSajuFromFastAPI(any(), any()))
                .willReturn(fixture.toFastApiResponse(BIRTH_DATE.toString(), BIRTH_TIME.toString()));
    }

    @Test
    @DisplayName("컨설팅과 궁합이 하루 3회를 공유하고 네 번째 요청은 거부된다")
    void shouldShareDailyLimitAcrossConsultationAndCompatibility() {
        // Given
        givenConsultationSucceeds();
        givenCompatibilitySucceeds();

        // When: 컨설팅 2회 + 궁합 1회
        analysisService.analyze(AnalysisType.CAREER_CONSULTATION, consultationRequest(), testUser.getId());
        analysisService.analyze(AnalysisType.CAREER_CONSULTATION, consultationRequest(), testUser.getId());
        analysisService.analyze(AnalysisType.COMPANY_COMPATIBILITY, compatibilityRequest(), testUser.getId());

        // Then: 네 번째는 유형과 무관하게 한도 초과
        assertThatThrownBy(() -> analysisService.analyze(
                AnalysisType.COMPANY_COMPATIBILITY, compatibilityRequest(), testUser.getId()))
                .isInstanceOf(DailyLimitExceededException.class);
        assertThat(usageCount()).isEqualTo(DAILY_LIMIT);
        assertThat(sajuRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("관운 분석은 쿼터를 차감하지 않는다")
    void shouldNotConsumeQuotaForSajuAnalysis() {
        // When
        for (int i = 0; i < 5; i++) {
            analysisService.analyze(AnalysisType.SAJU,
                    new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME), testUser.getId());
        }

        // Then
        assertThat(usageCount()).isZero();
        assertThat(sajuRepository.count()).isEqualTo(5);
    }

    @Test
    @DisplayName("외부 호출 실패는 차감한 쿼터를 원래 날짜로 복원한다")
    void shouldRestoreQuotaWhenExternalCallFails() {
        // Given
        given(consultationOpenAICaller.call(any(), any(), any(), any(), any()))
                .willThrow(new ExternalApiException("OpenAI 서버 오류"));

        // When / Then
        assertThatThrownBy(() -> analysisService.analyze(
                AnalysisType.CAREER_CONSULTATION, consultationRequest(), testUser.getId()))
                .isInstanceOf(ExternalApiException.class);
        assertThat(usageCount()).isZero();
        assertThat(sajuRepository.count()).isZero();
    }

    @Test
    @DisplayName("성공한 분석은 정확히 1회만 차감한다")
    void shouldConsumeExactlyOneQuotaPerSuccess() {
        // Given
        givenConsultationSucceeds();

        // When
        analysisService.analyze(AnalysisType.CAREER_CONSULTATION, consultationRequest(), testUser.getId());

        // Then
        assertThat(usageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("잔여 1회를 두고 동시에 요청하면 한 건만 성공한다")
    void shouldAllowOnlyOneRequestForLastRemainingQuota() throws Exception {
        // Given: 이미 2회 소진
        givenConsultationSucceeds();
        givenCompatibilitySucceeds();
        analysisService.analyze(AnalysisType.CAREER_CONSULTATION, consultationRequest(), testUser.getId());
        analysisService.analyze(AnalysisType.CAREER_CONSULTATION, consultationRequest(), testUser.getId());

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger succeeded = new AtomicInteger();
        List<Exception> failures = new CopyOnWriteArrayList<>();

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    analysisService.analyze(AnalysisType.COMPANY_COMPATIBILITY,
                            compatibilityRequest(), testUser.getId());
                    succeeded.incrementAndGet();
                } catch (DailyLimitExceededException expected) {
                    // 한도 초과는 정상 거부
                } catch (Exception e) {
                    failures.add(e);
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        done.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertThat(failures).isEmpty();
        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(usageCount()).isEqualTo(DAILY_LIMIT);
    }

    private void givenConsultationSucceeds() {
        given(consultationOpenAICaller.call(any(), any(), any(), any(), any()))
                .willReturn(advice());
    }

    private void givenCompatibilitySucceeds() {
        AnalysisTimeContext time = AnalysisTimeContext.from(clock);
        given(companyMatchingOpenAICaller.call(any(), any()))
                .willReturn(new CompatibilityNarrativeResponse(
                        "요약", "시너지", "경고", "오행 시너지", "약점 방어",
                        List.of(new CompatibilityNarrativeResponse.InterviewQuestion("질문", "의도")),
                        "주 직무 사유", "보조 직무 사유",
                        time.targetMonths().stream()
                                .map(month -> new CompatibilityNarrativeResponse.MonthlyAdvice(
                                        month, month + "월 조언"))
                                .toList(),
                        List.of("주의사항")));
    }

    private int usageCount() {
        return dailyApiUsageRepository
                .findByUserIdAndUsageDate(testUser.getId(), LocalDate.now(ClockConfig.SERVICE_ZONE))
                .map(usage -> usage.getRequestCount())
                .orElse(0);
    }

    private ConsultationRequest consultationRequest() {
        return new ConsultationRequest(BIRTH_DATE, BIRTH_TIME);
    }

    private CompatibilityRequest compatibilityRequest() {
        return new CompatibilityRequest(
                BIRTH_DATE, BIRTH_TIME,
                new CompatibilityRequest.TargetRoleRequest(JobCategoryEnum.TECH_BACKEND, "백엔드 개발자"),
                "테스트기업", LocalDate.of(2000, 1, 1), null);
    }

    private CareerAdviceResponse advice() {
        return new CareerAdviceResponse(
                List.of(new CareerAdviceResponse.IndustryRecommendation("IT", "적성", List.of("백엔드"))),
                List.of("면접 팁"), List.of("강점"), List.of("주의"),
                null, null, null, null, null, null, null, null, null,
                List.of("정관"), "일간 설명", "오행 설명");
    }
}
