package ssafy.SSAju.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.career.converter.AnalysisSnapshotCodec;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.AnalysisTimeContext;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.dto.response.AnalysisDetailResponse;
import ssafy.SSAju.dto.response.MyPageResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.repository.ManseRepository;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.service.SajuDataService;
import ssafy.SSAju.service.UserService;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 마이페이지 목록·상세가 새 saju 구조에서 소유권과 저장 스냅샷만으로 동작하는지 검증한다(T023).
 *
 * <p>조회는 부작용이 없어야 한다 — 외부 호출·새 분석 INSERT·쿼터 차감이 모두 0이다.
 */
@Testcontainers
@SpringBootTest
@DisplayName("마이페이지 분석 조회 통합 테스트 (MySQL)")
class UserAnalysisReadIntegrationTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);

    @Container
    static MySQLContainer<?> mysql = UnifiedAnalysisTestSupport.newMySqlContainer();

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        UnifiedAnalysisTestSupport.registerMySqlProperties(registry, mysql);
    }

    /** 조회 경로에서는 외부 호출이 전혀 없어야 하므로 호출 여부를 확인할 수 있게 대역으로 둔다. */
    @MockitoBean
    private SajuDataService sajuDataService;

    @Autowired private UserService userService;
    @Autowired private SajuRepository sajuRepository;
    @Autowired private ManseRepository manseRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AnalysisSnapshotCodec snapshotCodec;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final Clock clock = UnifiedAnalysisTestSupport.fixedClock();

    private User owner;
    private User otherOwner;
    private Manse manse;

    @BeforeEach
    void setUp() {
        sajuRepository.deleteAllInBatch();
        manseRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();

        owner = userRepository.save(UnifiedAnalysisTestSupport.newUser("read-owner@test.com"));
        otherOwner = userRepository.save(UnifiedAnalysisTestSupport.newUser("read-other@test.com"));

        UserProfile profile = userProfileRepository.save(
                UnifiedAnalysisTestSupport.newProfile(BIRTH_DATE, BIRTH_TIME));
        manse = manseRepository.saveAndFlush(Manse.builder()
                .userProfile(profile)
                .data(UnifiedAnalysisTestSupport.manseData())
                .fetchedAt(clock.instant())
                .build());
    }

    @Test
    @DisplayName("같은 출생정보를 공유해도 소유자별 이력만 보인다")
    void shouldListOnlyOwnHistory() {
        // Given
        save(owner.getId(), AnalysisType.SAJU);
        save(owner.getId(), AnalysisType.SAJU);
        save(otherOwner.getId(), AnalysisType.SAJU);

        // When
        MyPageResponse ownerPage = userService.getMyPage(owner.getId(), null, 0, 10);
        MyPageResponse otherPage = userService.getMyPage(otherOwner.getId(), null, 0, 10);

        // Then
        assertThat(ownerPage.analyses()).hasSize(2);
        assertThat(ownerPage.pagination().total()).isEqualTo(2);
        assertThat(otherPage.analyses()).hasSize(1);
        assertThat(otherPage.pagination().total()).isEqualTo(1);
    }

    @Test
    @DisplayName("분석이 없으면 빈 목록과 0건을 돌려준다")
    void shouldReturnEmptyHistory() {
        // When
        MyPageResponse page = userService.getMyPage(owner.getId(), null, 0, 10);

        // Then
        assertThat(page.analyses()).isEmpty();
        assertThat(page.pagination().total()).isZero();
        assertThat(page.pagination().totalPages()).isZero();
    }

    @Test
    @DisplayName("생성 시각이 같으면 숫자 ID 내림차순으로 정렬한다")
    void shouldSortByIdWhenCreatedAtTies() {
        // Given: 같은 시각의 세 건
        Instant sameInstant = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Long first = insertWithCreatedAt(owner.getId(), AnalysisType.SAJU, sameInstant);
        Long second = insertWithCreatedAt(owner.getId(), AnalysisType.SAJU, sameInstant);
        Long third = insertWithCreatedAt(owner.getId(), AnalysisType.SAJU, sameInstant);

        // When
        MyPageResponse page = userService.getMyPage(owner.getId(), null, 0, 10);

        // Then
        assertThat(page.analyses()).extracting("analysisId")
                .containsExactly(third, second, first);
    }

    @Test
    @DisplayName("1년보다 오래된 분석도 목록과 count에 포함된다")
    void shouldIncludeAnalysesOlderThanOneYear() {
        // Given
        Instant twoYearsAgo = clock.instant().minus(730, ChronoUnit.DAYS);
        Long oldId = insertWithCreatedAt(owner.getId(), AnalysisType.SAJU, twoYearsAgo);
        Long recentId = save(owner.getId(), AnalysisType.SAJU);

        // When
        MyPageResponse page = userService.getMyPage(owner.getId(), null, 0, 10);

        // Then
        assertThat(page.analyses()).extracting("analysisId").containsExactly(recentId, oldId);
        assertThat(page.pagination().total()).isEqualTo(2);
    }

    @Test
    @DisplayName("유형 필터의 목록과 count가 같은 조건을 쓴다")
    void shouldApplySameTypeConditionToListAndCount() {
        // Given
        save(owner.getId(), AnalysisType.SAJU);
        save(owner.getId(), AnalysisType.COMPANY_COMPATIBILITY);
        save(owner.getId(), AnalysisType.COMPANY_COMPATIBILITY);

        // When
        MyPageResponse compatibilityPage =
                userService.getMyPage(owner.getId(), AnalysisType.COMPANY_COMPATIBILITY, 0, 10);
        MyPageResponse consultationPage =
                userService.getMyPage(owner.getId(), AnalysisType.CAREER_CONSULTATION, 0, 10);

        // Then
        assertThat(compatibilityPage.analyses()).hasSize(2);
        assertThat(compatibilityPage.pagination().total()).isEqualTo(2);
        assertThat(compatibilityPage.analyses()).allSatisfy(dto ->
                assertThat(dto.targetName()).isEqualTo("테스트기업"));
        assertThat(consultationPage.analyses()).isEmpty();
        assertThat(consultationPage.pagination().total()).isZero();
    }

    @Test
    @DisplayName("페이지 크기를 넘기면 목록만 잘리고 전체 건수는 그대로다")
    void shouldPaginateWithoutChangingTotal() {
        // Given
        for (int i = 0; i < 3; i++) {
            save(owner.getId(), AnalysisType.SAJU);
        }

        // When
        MyPageResponse firstPage = userService.getMyPage(owner.getId(), null, 0, 2);
        MyPageResponse secondPage = userService.getMyPage(owner.getId(), null, 1, 2);

        // Then
        assertThat(firstPage.analyses()).hasSize(2);
        assertThat(secondPage.analyses()).hasSize(1);
        assertThat(firstPage.pagination().total()).isEqualTo(3);
        assertThat(firstPage.pagination().totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("상세를 반복 조회해도 같은 스냅샷이며 부작용이 없다")
    void shouldReturnSameSnapshotWithoutSideEffects() {
        // Given
        Long analysisId = save(owner.getId(), AnalysisType.SAJU);
        long sajuCountBefore = sajuRepository.count();
        long manseCountBefore = manseRepository.count();

        // When
        AnalysisDetailResponse first = userService.getAnalysisDetail(
                owner.getId(), analysisId, AnalysisType.SAJU);
        AnalysisDetailResponse second = userService.getAnalysisDetail(
                owner.getId(), analysisId, AnalysisType.SAJU);

        // Then
        assertThat(second).isEqualTo(first);
        assertThat(first.birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(sajuRepository.count()).isEqualTo(sajuCountBefore);
        assertThat(manseRepository.count()).isEqualTo(manseCountBefore);
        verifyNoInteractions(sajuDataService);
    }

    @Test
    @DisplayName("타인의 분석과 유형이 다른 분석은 조회되지 않는다")
    void shouldRejectOtherOwnerAndTypeMismatch() {
        // Given
        Long analysisId = save(owner.getId(), AnalysisType.SAJU);

        // When / Then
        assertThatThrownBy(() -> userService.getAnalysisDetail(
                otherOwner.getId(), analysisId, AnalysisType.SAJU))
                .isInstanceOf(SajuResultNotFoundException.class);
        assertThatThrownBy(() -> userService.getAnalysisDetail(
                owner.getId(), analysisId, AnalysisType.CAREER_CONSULTATION))
                .isInstanceOf(SajuResultNotFoundException.class);
        assertThatThrownBy(() -> userService.getAnalysisDetail(
                owner.getId(), 999_999L, AnalysisType.SAJU))
                .isInstanceOf(SajuResultNotFoundException.class);
    }

    private Long save(Long userId, AnalysisType type) {
        return sajuRepository.saveAndFlush(Saju.builder()
                .userId(userId)
                .analysisType(type)
                .manse(manse)
                .inputJson(inputJson(type))
                .resultJson(resultJson(type))
                .build()).getId();
    }

    /** {@code @CreatedDate}가 채우는 생성 시각을 직접 지정해야 하는 경우에만 사용한다. */
    private Long insertWithCreatedAt(Long userId, AnalysisType type, Instant createdAt) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO saju (user_id, analysis_type, manse_id, created_at, input_json, result_json)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """);
            ps.setLong(1, userId);
            ps.setString(2, type.name());
            ps.setLong(3, manse.getId());
            ps.setTimestamp(4, Timestamp.from(createdAt), utc);
            ps.setString(5, inputJson(type));
            ps.setString(6, resultJson(type));
            return ps;
        });
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM saju", Long.class);
    }

    private String inputJson(AnalysisType type) {
        if (type == AnalysisType.COMPANY_COMPATIBILITY) {
            return UnifiedAnalysisTestSupport.readFixture(
                    UnifiedAnalysisTestSupport.COMPATIBILITY_INPUT_FIXTURE);
        }
        AnalysisTimeContext time = AnalysisTimeContext.from(clock);
        return type == AnalysisType.SAJU
                ? snapshotCodec.writeInput(type, AnalysisPayload.SajuInput.of(BIRTH_DATE, BIRTH_TIME, time))
                : snapshotCodec.writeInput(type,
                        AnalysisPayload.ConsultationInput.of(BIRTH_DATE, BIRTH_TIME, time));
    }

    private String resultJson(AnalysisType type) {
        return switch (type) {
            case SAJU -> UnifiedAnalysisTestSupport.readFixture(
                    UnifiedAnalysisTestSupport.SAJU_RESULT_FIXTURE);
            case CAREER_CONSULTATION -> UnifiedAnalysisTestSupport.readFixture(
                    UnifiedAnalysisTestSupport.CONSULTATION_RESULT_FIXTURE);
            case COMPANY_COMPATIBILITY -> UnifiedAnalysisTestSupport.readFixture(
                    UnifiedAnalysisTestSupport.COMPATIBILITY_RESULT_FIXTURE);
        };
    }
}
