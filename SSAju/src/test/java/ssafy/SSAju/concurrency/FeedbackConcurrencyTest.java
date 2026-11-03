package ssafy.SSAju.concurrency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.career.dto.request.SatisfactionFeedbackRequest;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.SatisfactionStatus;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.FeedbackAlreadyExistsException;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;
import ssafy.SSAju.repository.ManseRepository;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.repository.UserSatisfactionFeedbackRepository;
import ssafy.SSAju.service.AuthService;
import ssafy.SSAju.service.FeedbackService;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 피드백의 "분석당 0..1개" 제약과 탈퇴 시 처리를 실제 MySQL에서 검증한다(T055, T067).
 */
@Testcontainers
@SpringBootTest
@DisplayName("피드백 제약·탈퇴 처리 테스트")
class FeedbackConcurrencyTest {

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

    @Autowired private FeedbackService feedbackService;
    @Autowired private AuthService authService;
    @Autowired private UserSatisfactionFeedbackRepository feedbackRepository;
    @Autowired private SajuRepository sajuRepository;
    @Autowired private ManseRepository manseRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User owner;
    private User otherOwner;
    private Manse manse;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_satisfaction_feedback");
        sajuRepository.deleteAllInBatch();
        manseRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM users");
        userProfileRepository.deleteAllInBatch();

        owner = userRepository.save(newUser("feedback-owner@test.com"));
        otherOwner = userRepository.save(newUser("feedback-other@test.com"));

        UserProfile profile = userProfileRepository.save(
                UnifiedAnalysisTestSupport.newProfile(BIRTH_DATE, BIRTH_TIME));
        manse = manseRepository.saveAndFlush(Manse.builder()
                .userProfile(profile)
                .data(UnifiedAnalysisTestSupport.manseData())
                .fetchedAt(Instant.now())
                .build());

        ManseData fixture = UnifiedAnalysisTestSupport.manseData();
        given(sajuDataService.fetchSajuFromFastAPI(any(), any()))
                .willReturn(fixture.toFastApiResponse(BIRTH_DATE.toString(), BIRTH_TIME.toString()));
    }

    @Test
    @DisplayName("같은 분석에 동시에 제출해도 피드백은 한 건만 남는다")
    void shouldKeepSingleFeedbackUnderConcurrentSubmission() throws Exception {
        // Given
        Long analysisId = saveAnalysis(owner.getId(), AnalysisType.CAREER_CONSULTATION);
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        List<Exception> unexpected = new CopyOnWriteArrayList<>();

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    feedbackService.saveFeedback(request(analysisId), owner.getId());
                    succeeded.incrementAndGet();
                } catch (FeedbackAlreadyExistsException expected) {
                    duplicates.incrementAndGet();
                } catch (Exception e) {
                    unexpected.add(e);
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
        assertThat(unexpected).isEmpty();
        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(duplicates.get()).isEqualTo(threadCount - 1);
        assertThat(feedbackRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 분석의 재제출은 409로 거부한다")
    void shouldRejectSecondFeedbackForSameAnalysis() {
        // Given
        Long analysisId = saveAnalysis(owner.getId(), AnalysisType.COMPANY_COMPATIBILITY);
        feedbackService.saveFeedback(request(analysisId), owner.getId());

        // When / Then
        assertThatThrownBy(() -> feedbackService.saveFeedback(request(analysisId), owner.getId()))
                .isInstanceOf(FeedbackAlreadyExistsException.class);
        assertThat(feedbackRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("작성자와 대상 분석이 없는 피드백은 만들 수 없다")
    void shouldRequireAuthorAndAnalysis() {
        Saju saju = sajuRepository.findById(saveAnalysis(owner.getId(), AnalysisType.CAREER_CONSULTATION))
                .orElseThrow();

        assertThatThrownBy(() -> ssafy.SSAju.career.entity.UserSatisfactionFeedback.builder()
                .user(null).saju(saju).satisfactionStatus(SatisfactionStatus.SATISFIED).build())
                .isInstanceOf(InvalidSajuDataException.class);
        assertThatThrownBy(() -> ssafy.SSAju.career.entity.UserSatisfactionFeedback.builder()
                .user(owner).saju(null).satisfactionStatus(SatisfactionStatus.SATISFIED).build())
                .isInstanceOf(InvalidSajuDataException.class);
    }

    @Test
    @DisplayName("탈퇴는 본인 피드백만 지우고 분석과 타인 피드백은 남긴다")
    void shouldDeleteOnlyOwnFeedbackOnWithdrawal() {
        // Given
        Long ownerAnalysis = saveAnalysis(owner.getId(), AnalysisType.CAREER_CONSULTATION);
        Long otherAnalysis = saveAnalysis(otherOwner.getId(), AnalysisType.CAREER_CONSULTATION);
        feedbackService.saveFeedback(request(ownerAnalysis), owner.getId());
        feedbackService.saveFeedback(request(otherAnalysis), otherOwner.getId());

        // When
        authService.deleteUser(owner.getId(), RAW_PASSWORD);

        // Then
        assertThat(feedbackRepository.count()).isEqualTo(1);
        assertThat(sajuRepository.count()).isEqualTo(2);
        assertThat(sajuRepository.findById(ownerAnalysis)).isPresent();
    }

    @Test
    @DisplayName("탈퇴한 사용자는 새 피드백을 남길 수 없다")
    void shouldRejectFeedbackAfterWithdrawal() {
        // Given
        Long analysisId = saveAnalysis(owner.getId(), AnalysisType.CAREER_CONSULTATION);
        authService.deleteUser(owner.getId(), RAW_PASSWORD);

        // When / Then
        assertThatThrownBy(() -> feedbackService.saveFeedback(request(analysisId), owner.getId()))
                .isInstanceOf(UserNotFoundException.class);
        assertThat(feedbackRepository.count()).isZero();
    }

    private User newUser(String email) {
        return User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .name("피드백테스터")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(Instant.now())
                .privacyAgreedAt(Instant.now())
                .build();
    }

    private Long saveAnalysis(Long userId, AnalysisType type) {
        return sajuRepository.saveAndFlush(Saju.builder()
                .userId(userId)
                .analysisType(type)
                .manse(manse)
                .inputJson(UnifiedAnalysisTestSupport.readFixture(
                        UnifiedAnalysisTestSupport.COMPATIBILITY_INPUT_FIXTURE))
                .resultJson(UnifiedAnalysisTestSupport.readFixture(
                        UnifiedAnalysisTestSupport.COMPATIBILITY_RESULT_FIXTURE))
                .build()).getId();
    }

    private SatisfactionFeedbackRequest request(Long analysisId) {
        return new SatisfactionFeedbackRequest(analysisId, SatisfactionStatus.SATISFIED, "좋았습니다");
    }
}
