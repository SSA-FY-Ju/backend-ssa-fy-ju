package ssafy.SSAju.concurrency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.dto.request.CareerTimingRequest;
import ssafy.SSAju.dto.request.DefaultProfileRequest;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;
import ssafy.SSAju.repository.ManseRepository;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.service.AnalysisService;
import ssafy.SSAju.service.AuthService;
import ssafy.SSAju.service.DefaultProfileService;
import ssafy.SSAju.service.SajuDataService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 회원 탈퇴와 최종 쓰기(분석 저장·기본 출생정보 저장)의 순서를 검증한다(T047).
 *
 * <p>탈퇴 전에 커밋한 분석은 남고, 탈퇴가 먼저 끝난 뒤의 최종 저장은 거부된다. 동시에 일어나면
 * 사용자 행 잠금이 순서를 정하므로 둘 중 하나로 결정되며, 어느 쪽이든 "저장됐는데 탈퇴가
 * 무시되거나" "거부됐는데 행이 남는" 상태는 생기지 않는다.
 */
@Testcontainers
@SpringBootTest
@DisplayName("회원 탈퇴와 최종 쓰기 순서 테스트")
class UserLifecycleConcurrencyTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);
    private static final String RAW_PASSWORD = "Test1234!";

    @Container
    static MySQLContainer<?> mysql = UnifiedAnalysisTestSupport.newMySqlContainer();

    @Container
    static GenericContainer<?> redis = UnifiedAnalysisTestSupport.newRedisContainer();

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        UnifiedAnalysisTestSupport.registerMySqlProperties(registry, mysql);
        UnifiedAnalysisTestSupport.registerRedisProperties(registry, redis);
    }

    @MockitoBean private SajuDataService sajuDataService;

    @Autowired private AuthService authService;
    @Autowired private AnalysisService analysisService;
    @Autowired private DefaultProfileService defaultProfileService;
    @Autowired private UserRepository userRepository;
    @Autowired private SajuRepository sajuRepository;
    @Autowired private ManseRepository manseRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long userId;

    @BeforeEach
    void setUp() {
        sajuRepository.deleteAllInBatch();
        manseRepository.deleteAllInBatch();
        userRepository.deleteAll();
        userProfileRepository.deleteAllInBatch();

        User user = userRepository.save(User.builder()
                .email("lifecycle@test.com")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .name("탈퇴테스터")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(Instant.now())
                .privacyAgreedAt(Instant.now())
                .build());
        userId = user.getId();

        ManseData fixture = UnifiedAnalysisTestSupport.manseData();
        given(sajuDataService.fetchSajuFromFastAPI(any(), any()))
                .willReturn(fixture.toFastApiResponse(BIRTH_DATE.toString(), BIRTH_TIME.toString()));
    }

    @Test
    @DisplayName("탈퇴 전에 커밋한 분석은 탈퇴 후에도 남는다")
    void shouldKeepAnalysisCommittedBeforeWithdrawal() {
        // Given
        Long analysisId = analysisService.analyze(AnalysisType.SAJU,
                new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME), userId).analysisId();

        // When
        authService.deleteUser(userId, RAW_PASSWORD);

        // Then: 분석과 공유 기초는 유지되고 소유자 연결도 남는다
        assertThat(sajuRepository.findById(analysisId)).isPresent();
        assertThat(sajuRepository.findById(analysisId).orElseThrow().getUserId()).isEqualTo(userId);
        assertThat(manseRepository.count()).isEqualTo(1);
        assertThat(userProfileRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴가 끝난 뒤의 분석 저장은 거부된다")
    void shouldRejectAnalysisAfterWithdrawal() {
        // Given
        authService.deleteUser(userId, RAW_PASSWORD);

        // When / Then
        assertThatThrownBy(() -> analysisService.analyze(AnalysisType.SAJU,
                new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME), userId))
                .isInstanceOf(UserNotFoundException.class);
        assertThat(sajuRepository.count()).isZero();
    }

    @Test
    @DisplayName("탈퇴가 끝난 뒤의 기본 출생정보 저장도 거부된다")
    void shouldRejectDefaultProfileSaveAfterWithdrawal() {
        // Given
        authService.deleteUser(userId, RAW_PASSWORD);

        // When / Then
        assertThatThrownBy(() -> defaultProfileService.changeDefaultProfile(
                userId, new DefaultProfileRequest(BIRTH_DATE, BIRTH_TIME)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("탈퇴는 기본 출생정보 참조만 해제하고 공유 프로필은 남긴다")
    void shouldReleaseOnlyDefaultProfileReference() {
        // Given
        defaultProfileService.changeDefaultProfile(userId, new DefaultProfileRequest(BIRTH_DATE, BIRTH_TIME));

        // When
        authService.deleteUser(userId, RAW_PASSWORD);

        // Then: 공유 프로필 행은 남고 참조만 비워진다
        assertThat(userProfileRepository.count()).isEqualTo(1);
        assertThat(userRepository.findById(userId)).isEmpty(); // soft delete 필터
    }

    @Test
    @DisplayName("분석 저장과 탈퇴가 동시에 일어나도 결과가 어긋나지 않는다")
    void shouldOrderConcurrentAnalysisAndWithdrawal() throws Exception {
        // Given
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicBoolean analysisSucceeded = new AtomicBoolean(false);
        List<Exception> unexpected = new CopyOnWriteArrayList<>();

        // When
        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                analysisService.analyze(AnalysisType.SAJU,
                        new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME), userId);
                analysisSucceeded.set(true);
            } catch (UserNotFoundException expected) {
                // 탈퇴가 먼저 끝난 경우의 정상 거부
            } catch (Exception e) {
                unexpected.add(e);
            } finally {
                done.countDown();
            }
        });
        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                authService.deleteUser(userId, RAW_PASSWORD);
            } catch (Exception e) {
                unexpected.add(e);
            } finally {
                done.countDown();
            }
        });
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        done.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: 저장 성공 여부와 실제 행 수가 일치한다
        assertThat(unexpected).isEmpty();
        assertThat(sajuRepository.count()).isEqualTo(analysisSucceeded.get() ? 1 : 0);
        assertThat(userRepository.findById(userId)).isEmpty();
    }
}
