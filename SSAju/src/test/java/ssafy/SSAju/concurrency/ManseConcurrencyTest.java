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
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.provider.ManseProvider;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;
import ssafy.SSAju.repository.ManseRepository;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.service.SajuDataService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * 같은 프로필의 만세력을 동시에 처음 요청해도 분산락 없이 한 행으로 수렴하는지 검증한다(T015).
 *
 * <p>외부 호출 중복까지 막지는 않는다 — 같은 출생정보의 계산은 결정론적이고, 중복 호출을 막으려면
 * 다시 락이 필요해진다. 보장하는 것은 "만세력 행은 프로필당 하나"다.
 */
@Testcontainers
@SpringBootTest
@DisplayName("만세력 동시 생성 수렴 테스트")
class ManseConcurrencyTest {

    private static final int THREAD_COUNT = 10;
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

    @Autowired private ManseProvider manseProvider;
    @Autowired private ManseRepository manseRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private SajuRepository sajuRepository;

    private UserProfile profile;

    @BeforeEach
    void setUp() {
        sajuRepository.deleteAllInBatch();
        manseRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();

        profile = userProfileRepository.save(
                UnifiedAnalysisTestSupport.newProfile(BIRTH_DATE, BIRTH_TIME));

        ManseData fixture = UnifiedAnalysisTestSupport.manseData();
        given(sajuDataService.fetchSajuFromFastAPI(any(), any()))
                .willReturn(fixture.toFastApiResponse(BIRTH_DATE.toString(), BIRTH_TIME.toString()));
    }

    @Test
    @DisplayName("같은 프로필의 동시 최초 요청이 만세력 한 행으로 수렴한다")
    void shouldConvergeToSingleManse() throws Exception {
        // Given
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);
        List<Long> manseIds = new CopyOnWriteArrayList<>();
        List<Exception> failures = new CopyOnWriteArrayList<>();

        // When
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    Manse manse = manseProvider.findOrCreate(profile);
                    manseIds.add(manse.getId());
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
        assertThat(manseIds).hasSize(THREAD_COUNT);
        Set<Long> distinctIds = manseIds.stream().collect(Collectors.toSet());
        assertThat(distinctIds).hasSize(1);
        assertThat(manseRepository.count()).isEqualTo(1);
        // 외부 호출 중복은 허용한다 — 최소 한 번은 실제로 호출됐는지만 확인한다.
        verify(sajuDataService, atLeastOnce()).fetchSajuFromFastAPI(any(), any());
    }
}
