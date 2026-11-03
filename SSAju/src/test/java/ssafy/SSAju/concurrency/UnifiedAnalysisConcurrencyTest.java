package ssafy.SSAju.concurrency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.career.domain.AnalysisExecution;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.dto.request.CareerTimingRequest;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;
import ssafy.SSAju.repository.ManseRepository;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.service.AnalysisService;
import ssafy.SSAju.service.SajuDataService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 같은 입력의 분석 요청이 동시에 성공해도 각각 자기 결과와 별도 ID를 받는지 검증한다(T015).
 *
 * <p>프로필·만세력은 공유되어 한 건으로 수렴하지만 결과는 합쳐지지 않는다. 관운 유형을 쓰는 이유는
 * 외부 AI 호출 없이 공통 실행 경로(검증 → 기초 확보 → 전략 → 저장)를 그대로 지나기 때문이다.
 */
@Testcontainers
@SpringBootTest
@DisplayName("통합 분석 동시 요청 테스트")
class UnifiedAnalysisConcurrencyTest {

    private static final int THREAD_COUNT = 4;
    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);

    @Container
    static MySQLContainer<?> mysql = UnifiedAnalysisTestSupport.newMySqlContainer();

    @Container
    static GenericContainer<?> redis = UnifiedAnalysisTestSupport.newRedisContainer();

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        UnifiedAnalysisTestSupport.registerMySqlProperties(registry, mysql);
        UnifiedAnalysisTestSupport.registerRedisProperties(registry, redis);
    }

    @MockitoBean
    private SajuDataService sajuDataService;

    @Autowired private AnalysisService analysisService;
    @Autowired private SajuRepository sajuRepository;
    @Autowired private ManseRepository manseRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        sajuRepository.deleteAllInBatch();
        manseRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();

        User user = userRepository.save(UnifiedAnalysisTestSupport.newUser("concurrency@test.com"));
        userId = user.getId();

        ManseData fixture = UnifiedAnalysisTestSupport.manseData();
        given(sajuDataService.fetchSajuFromFastAPI(any(), any()))
                .willReturn(fixture.toFastApiResponse(BIRTH_DATE.toString(), BIRTH_TIME.toString()));
    }

    @Test
    @DisplayName("같은 입력의 동시 요청이 각각 다른 숫자 ID의 결과를 남긴다")
    void shouldKeepConcurrentResultsSeparate() throws Exception {
        // Given
        CareerTimingRequest request = new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);
        List<Long> analysisIds = new CopyOnWriteArrayList<>();
        List<Exception> failures = new CopyOnWriteArrayList<>();

        // When
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    AnalysisExecution execution = analysisService.analyze(AnalysisType.SAJU, request, userId);
                    analysisIds.add(execution.analysisId());
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
        assertThat(analysisIds).hasSize(THREAD_COUNT).doesNotHaveDuplicates();
        assertThat(sajuRepository.count()).isEqualTo(THREAD_COUNT);
        // 공유 기초는 중복 생성되지 않는다
        assertThat(userProfileRepository.count()).isEqualTo(1);
        assertThat(manseRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 입력을 순차로 3번 요청해도 결과 3건이 각각 남는다")
    void shouldStoreEveryRepeatedRequest() {
        // Given
        CareerTimingRequest request = new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME);

        // When
        Long first = analysisService.analyze(AnalysisType.SAJU, request, userId).analysisId();
        Long second = analysisService.analyze(AnalysisType.SAJU, request, userId).analysisId();
        Long third = analysisService.analyze(AnalysisType.SAJU, request, userId).analysisId();

        // Then
        assertThat(List.of(first, second, third)).doesNotHaveDuplicates();
        assertThat(sajuRepository.count()).isEqualTo(3);
        assertThat(userProfileRepository.count()).isEqualTo(1);
        assertThat(manseRepository.count()).isEqualTo(1);
    }
}
