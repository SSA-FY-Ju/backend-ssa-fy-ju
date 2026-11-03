package ssafy.SSAju.admin.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.admin.dto.AnalyticsDetailDTO;
import ssafy.SSAju.admin.dto.AnalyticsListDTO;
import ssafy.SSAju.admin.service.AdminBaseService;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;
import ssafy.SSAju.repository.ManseRepository;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 분석 조회가 새 saju 단일 집계로 동작하는지 검증한다(T056).
 *
 * <p>세 유형이 한 테이블에 모였으므로 "정본 수와 접근자 수가 다른" 문제가 사라졌고, 한 행이 성공
 * 분석 한 건이다. 서울 날짜 경계와 탈퇴 사용자 포함 여부를 함께 확인한다.
 */
@Testcontainers
@SpringBootTest
@DisplayName("AdminAnalyticsQueryRepository — 새 분석 구조 집계 검증")
class AdminAnalyticsQueryRepositoryTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);

    @Container
    static MySQLContainer<?> mysql = UnifiedAnalysisTestSupport.newMySqlContainer();

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        UnifiedAnalysisTestSupport.registerMySqlProperties(registry, mysql);
    }

    @Autowired private AdminAnalyticsQueryRepository analyticsQueryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private ManseRepository manseRepository;
    @Autowired private SajuRepository sajuRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User owner;
    private Manse manse;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_satisfaction_feedback");
        sajuRepository.deleteAllInBatch();
        manseRepository.deleteAllInBatch();
        // soft delete된 계정은 @SQLRestriction 때문에 JPA deleteAll에서 빠지므로 직접 지운다.
        jdbcTemplate.update("DELETE FROM users");
        userProfileRepository.deleteAllInBatch();

        owner = userRepository.save(UnifiedAnalysisTestSupport.newUser("admin-analytics@test.com"));
        UserProfile profile = userProfileRepository.save(
                UnifiedAnalysisTestSupport.newProfile(BIRTH_DATE, BIRTH_TIME));
        manse = manseRepository.saveAndFlush(Manse.builder()
                .userProfile(profile)
                .data(UnifiedAnalysisTestSupport.manseData())
                .fetchedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("유형별 당일 집계는 저장된 분석 행 수와 같다")
    void shouldCountEachAnalysisRowOnce() {
        // Given
        LocalDate today = LocalDate.now(AdminBaseService.SEOUL_ZONE);
        insertAt(AnalysisType.SAJU, seoulInstant(today, 10, 0));
        insertAt(AnalysisType.SAJU, seoulInstant(today, 11, 0));
        insertAt(AnalysisType.CAREER_CONSULTATION, seoulInstant(today, 12, 0));

        // When
        Map<String, Long> summary = analyticsQueryRepository.findDailyAnalysisSummary(today);

        // Then
        assertThat(summary.get("SAJU")).isEqualTo(2);
        assertThat(summary.get("CAREER_CONSULTATION")).isEqualTo(1);
        assertThat(summary.get("COMPANY_COMPATIBILITY")).isZero();
    }

    @Test
    @DisplayName("목록은 숫자 ID·소유자·유형을 돌려주고 최신순으로 정렬한다")
    void shouldReturnListOrderedByCreatedAtDesc() {
        // Given
        LocalDate today = LocalDate.now(AdminBaseService.SEOUL_ZONE);
        Long older = insertAt(AnalysisType.SAJU, seoulInstant(today, 9, 0));
        Long newer = insertAt(AnalysisType.SAJU, seoulInstant(today, 18, 0));

        // When
        List<AnalyticsListDTO> list = analyticsQueryRepository
                .findAnalyticsByDateAndType(null, today, today, 0, 20);

        // Then
        assertThat(list).extracting(AnalyticsListDTO::id).containsExactly(newer, older);
        assertThat(list).allSatisfy(dto -> {
            assertThat(dto.userId()).isEqualTo(owner.getId());
            assertThat(dto.analysisType()).isEqualTo(AnalysisType.SAJU.name());
        });
    }

    @Test
    @DisplayName("유형 필터는 해당 유형만 돌려준다")
    void shouldFilterByAnalysisType() {
        // Given
        LocalDate today = LocalDate.now(AdminBaseService.SEOUL_ZONE);
        insertAt(AnalysisType.SAJU, seoulInstant(today, 9, 0));
        Long compatibilityId = insertAt(AnalysisType.COMPANY_COMPATIBILITY, seoulInstant(today, 10, 0));

        // When
        List<AnalyticsListDTO> list = analyticsQueryRepository.findAnalyticsByDateAndType(
                AnalysisType.COMPANY_COMPATIBILITY.name(), today, today, 0, 20);

        // Then
        assertThat(list).extracting(AnalyticsListDTO::id).containsExactly(compatibilityId);
    }

    @Test
    @DisplayName("서울 자정 경계로 날짜를 가른다")
    void shouldSplitBySeoulMidnight() {
        // Given
        LocalDate today = LocalDate.now(AdminBaseService.SEOUL_ZONE);
        Long lastSecondOfToday = insertAt(AnalysisType.SAJU, seoulInstant(today, 23, 59));
        Long firstSecondOfTomorrow = insertAt(AnalysisType.SAJU, seoulInstant(today.plusDays(1), 0, 0));

        // When
        List<AnalyticsListDTO> todayList = analyticsQueryRepository
                .findAnalyticsByDateAndType(null, today, today, 0, 20);
        Map<String, Long> todaySummary = analyticsQueryRepository.findDailyAnalysisSummary(today);

        // Then
        assertThat(todayList).extracting(AnalyticsListDTO::id)
                .containsExactly(lastSecondOfToday)
                .doesNotContain(firstSecondOfTomorrow);
        assertThat(todaySummary.get("SAJU")).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴한 사용자의 분석도 집계와 목록에 남는다")
    void shouldIncludeAnalysisOfWithdrawnUser() {
        // Given
        LocalDate today = LocalDate.now(AdminBaseService.SEOUL_ZONE);
        Long analysisId = insertAt(AnalysisType.SAJU, seoulInstant(today, 10, 0));
        jdbcTemplate.update("UPDATE users SET deleted_at = NOW(), status = 'INACTIVE' WHERE id = ?",
                owner.getId());

        // When
        List<AnalyticsListDTO> list = analyticsQueryRepository
                .findAnalyticsByDateAndType(null, today, today, 0, 20);
        Map<String, Long> summary = analyticsQueryRepository.findDailyAnalysisSummary(today);

        // Then: User 엔티티 조인을 쓰지 않으므로 soft delete 필터에 가려지지 않는다
        assertThat(list).extracting(AnalyticsListDTO::id).containsExactly(analysisId);
        assertThat(summary.get("SAJU")).isEqualTo(1);
    }

    @Test
    @DisplayName("상세는 분석 ID·유형·소유자가 모두 맞아야 조회된다")
    void shouldRequireIdTypeAndOwnerForDetail() {
        // Given
        LocalDate today = LocalDate.now(AdminBaseService.SEOUL_ZONE);
        Long analysisId = insertAt(AnalysisType.CAREER_CONSULTATION, seoulInstant(today, 10, 0));

        // When
        Optional<AnalyticsDetailDTO> found = analyticsQueryRepository.findAnalyticsById(
                analysisId, AnalysisType.CAREER_CONSULTATION.name(), owner.getId());
        Optional<AnalyticsDetailDTO> wrongType = analyticsQueryRepository.findAnalyticsById(
                analysisId, AnalysisType.SAJU.name(), owner.getId());
        Optional<AnalyticsDetailDTO> wrongOwner = analyticsQueryRepository.findAnalyticsById(
                analysisId, AnalysisType.CAREER_CONSULTATION.name(), owner.getId() + 999);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().jsonData()).contains("schemaVersion");
        assertThat(wrongType).isEmpty();
        assertThat(wrongOwner).isEmpty();
    }

    private Instant seoulInstant(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(AdminBaseService.SEOUL_ZONE).toInstant();
    }

    /** 생성 시각을 지정해야 하므로 감사 시각 대신 직접 INSERT한다. */
    private Long insertAt(AnalysisType type, Instant createdAt) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        String resultJson = switch (type) {
            case SAJU -> UnifiedAnalysisTestSupport.readFixture(
                    UnifiedAnalysisTestSupport.SAJU_RESULT_FIXTURE);
            case CAREER_CONSULTATION -> UnifiedAnalysisTestSupport.readFixture(
                    UnifiedAnalysisTestSupport.CONSULTATION_RESULT_FIXTURE);
            case COMPANY_COMPATIBILITY -> UnifiedAnalysisTestSupport.readFixture(
                    UnifiedAnalysisTestSupport.COMPATIBILITY_RESULT_FIXTURE);
        };
        String inputJson = UnifiedAnalysisTestSupport.readFixture(
                UnifiedAnalysisTestSupport.COMPATIBILITY_INPUT_FIXTURE);

        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO saju (user_id, analysis_type, manse_id, created_at, input_json, result_json)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """);
            ps.setLong(1, owner.getId());
            ps.setString(2, type.name());
            ps.setLong(3, manse.getId());
            ps.setTimestamp(4, Timestamp.from(createdAt), utc);
            ps.setString(5, inputJson);
            ps.setString(6, resultJson);
            return ps;
        });
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM saju", Long.class);
    }
}
