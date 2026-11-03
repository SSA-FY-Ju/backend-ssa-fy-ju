package ssafy.SSAju.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
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
import ssafy.SSAju.career.enums.CompanyFoundingDateSource;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.repository.ManseRepository;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 새 저장 구조의 JSON·enum·숫자 ID·UTC 시각이 실제 MySQL에서 왕복되는지 검증한다(T030).
 *
 * <p>요청값과 실제 계산에 사용한 값을 구분해 저장하는지, 결과 스냅샷의 중첩 필드가 손실 없이
 * 돌아오는지, 손상된 JSON이 조용히 통과하지 않는지를 함께 확인한다.
 */
@Testcontainers
@SpringBootTest
@DisplayName("분석 스냅샷 저장/조회 통합 테스트 (MySQL)")
class CareerResultJsonStorageIntegrationTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);

    @Container
    static MySQLContainer<?> mysql = UnifiedAnalysisTestSupport.newMySqlContainer();

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        UnifiedAnalysisTestSupport.registerMySqlProperties(registry, mysql);
    }

    @Autowired private SajuRepository sajuRepository;
    @Autowired private ManseRepository manseRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AnalysisSnapshotCodec snapshotCodec;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private final Clock clock = UnifiedAnalysisTestSupport.fixedClock();

    private User owner;
    private Manse manse;

    @BeforeEach
    void setUp() {
        sajuRepository.deleteAllInBatch();
        manseRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();

        owner = userRepository.save(UnifiedAnalysisTestSupport.newUser("json-storage@test.com"));
        UserProfile profile = userProfileRepository.save(
                UnifiedAnalysisTestSupport.newProfile(BIRTH_DATE, BIRTH_TIME));
        manse = manseRepository.saveAndFlush(Manse.builder()
                .userProfile(profile)
                .data(UnifiedAnalysisTestSupport.manseData())
                .fetchedAt(clock.instant())
                .build());
    }

    @Test
    @DisplayName("컨설팅 결과의 모든 중첩 필드가 손실 없이 돌아온다")
    void shouldRoundTripEveryConsultationField() {
        // Given
        String resultJson = UnifiedAnalysisTestSupport.readFixture(
                UnifiedAnalysisTestSupport.CONSULTATION_RESULT_FIXTURE);
        AnalysisPayload.ConsultationResultData expected =
                (AnalysisPayload.ConsultationResultData) snapshotCodec.readResult(
                        AnalysisType.CAREER_CONSULTATION, resultJson);

        // When
        Long id = save(AnalysisType.CAREER_CONSULTATION, consultationInputJson(), resultJson);
        AnalysisPayload.ConsultationResultData reloaded =
                (AnalysisPayload.ConsultationResultData) snapshotCodec.readResult(
                        AnalysisType.CAREER_CONSULTATION,
                        sajuRepository.findById(id).orElseThrow().getResultJson());

        // Then
        assertThat(reloaded).isEqualTo(expected);
        assertThat(reloaded.sajuProfile().dayMaster()).isEqualTo("己");
        assertThat(reloaded.sajuProfile().tenGodDistribution()).containsEntry("정관", 1);
        assertThat(reloaded.openaiModelVersion()).isEqualTo("gpt-4o-mini");
        assertThat(reloaded.analysisSummary()).isNotBlank();
        assertThat(reloaded.careerTimeline().months()).containsKey(1);
        assertThat(reloaded.powerKeywords().keywords()).hasSize(1);
    }

    @Test
    @DisplayName("궁합 결과의 모든 중첩 필드와 월별 운세가 손실 없이 돌아온다")
    void shouldRoundTripEveryCompatibilityField() {
        // Given
        String resultJson = UnifiedAnalysisTestSupport.readFixture(
                UnifiedAnalysisTestSupport.COMPATIBILITY_RESULT_FIXTURE);

        // When
        Long id = save(AnalysisType.COMPANY_COMPATIBILITY, compatibilityInputJson(), resultJson);
        AnalysisPayload.CompatibilityResultData reloaded =
                (AnalysisPayload.CompatibilityResultData) snapshotCodec.readResult(
                        AnalysisType.COMPANY_COMPATIBILITY,
                        sajuRepository.findById(id).orElseThrow().getResultJson());

        // Then
        assertThat(reloaded.compatibilityScore()).isEqualTo(78);
        assertThat(reloaded.requestContext().companyName()).isEqualTo("테스트기업");
        assertThat(reloaded.monthlyForecast()).hasSize(5);
        assertThat(reloaded.monthlyForecast().get(0).status()).isNotNull();
        assertThat(reloaded.actionableStrategy().bestTiming().luckyDays()).hasSize(3);
        assertThat(reloaded.roleCompatibility()).hasSize(2);
    }

    @Test
    @DisplayName("요청값과 실제 계산에 사용한 값을 구분해 저장한다")
    void shouldKeepRequestedAndResolvedInputsApart() {
        // Given: 설립 시각을 요청하지 않아 12:00으로 정규화된 입력
        Long id = save(AnalysisType.COMPANY_COMPATIBILITY, compatibilityInputJson(),
                UnifiedAnalysisTestSupport.readFixture(UnifiedAnalysisTestSupport.COMPATIBILITY_RESULT_FIXTURE));

        // When
        AnalysisPayload.CompatibilityInput input =
                (AnalysisPayload.CompatibilityInput) snapshotCodec.readInput(
                        AnalysisType.COMPANY_COMPATIBILITY,
                        sajuRepository.findById(id).orElseThrow().getInputJson());

        // Then
        assertThat(input.requestedCompanyFoundingTime()).isNull();
        assertThat(input.resolvedCompanyFoundingTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(input.companyFoundingDateSource()).isEqualTo(CompanyFoundingDateSource.REQUEST);
        assertThat(input.compatibilityMonth()).isEqualTo(202611);
    }

    @Test
    @DisplayName("숫자 ID와 enum 유형이 저장 형식 그대로 돌아온다")
    void shouldRoundTripNumericIdAndEnum() {
        // When
        Long id = save(AnalysisType.CAREER_CONSULTATION, consultationInputJson(),
                UnifiedAnalysisTestSupport.readFixture(UnifiedAnalysisTestSupport.CONSULTATION_RESULT_FIXTURE));

        // Then
        Saju reloaded = sajuRepository.findById(id).orElseThrow();
        assertThat(reloaded.getId()).isInstanceOf(Long.class).isEqualTo(id);
        assertThat(reloaded.getAnalysisType()).isEqualTo(AnalysisType.CAREER_CONSULTATION);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT analysis_type FROM saju WHERE id = ?", String.class, id))
                .isEqualTo("CAREER_CONSULTATION");
    }

    @Test
    @DisplayName("JVM 기본 시간대가 UTC가 아니어도 마이크로초 Instant가 그대로 왕복한다")
    @Transactional
    void shouldRoundTripMicrosecondInstantRegardlessOfJvmTimeZone() {
        // Given: 비UTC 기본 시간대와 마이크로초 정밀도 시각
        TimeZone originalTimeZone = TimeZone.getDefault();
        Instant fetchedAt = Instant.parse("2026-11-02T03:04:05.123456Z");
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));

            UserProfile otherProfile = userProfileRepository.save(
                    UnifiedAnalysisTestSupport.newProfile(LocalDate.of(1988, 7, 7), LocalTime.of(6, 30)));
            Manse saved = manseRepository.saveAndFlush(Manse.builder()
                    .userProfile(otherProfile)
                    .data(UnifiedAnalysisTestSupport.manseData())
                    .fetchedAt(fetchedAt)
                    .build());
            entityManager.flush();
            entityManager.clear();

            // When: JPA와 UTC Calendar를 전달한 JDBC로 각각 읽는다
            Instant viaJpa = manseRepository.findById(saved.getId()).orElseThrow().getFetchedAt();
            Timestamp viaJdbc = jdbcTemplate.query(
                    "SELECT fetched_at FROM manse WHERE id = ?",
                    rs -> {
                        rs.next();
                        return rs.getTimestamp("fetched_at",
                                Calendar.getInstance(TimeZone.getTimeZone("UTC")));
                    },
                    saved.getId());

            // Then
            assertThat(viaJpa).isEqualTo(fetchedAt);
            assertThat(viaJdbc).isNotNull();
            assertThat(viaJdbc.toInstant()).isEqualTo(fetchedAt);
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    @DisplayName("알 수 없는 유형·지원하지 않는 버전·손상된 JSON은 거부한다")
    void shouldRejectUnknownTypeUnsupportedVersionAndDamagedJson() {
        // Given
        String consultationJson = UnifiedAnalysisTestSupport.readFixture(
                UnifiedAnalysisTestSupport.CONSULTATION_RESULT_FIXTURE);

        // 유형 없음
        assertThatThrownBy(() -> snapshotCodec.readResult(null, consultationJson))
                .isInstanceOf(InvalidSajuDataException.class);

        // 지원하지 않는 버전
        assertThatThrownBy(() -> snapshotCodec.readResult(AnalysisType.CAREER_CONSULTATION,
                consultationJson.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")))
                .isInstanceOf(InvalidSajuDataException.class);

        // 손상된 JSON
        assertThatThrownBy(() -> snapshotCodec.readResult(AnalysisType.CAREER_CONSULTATION,
                consultationJson.substring(0, consultationJson.length() / 2)))
                .isInstanceOf(DataAccessException.class);

        // 빈 값
        assertThatThrownBy(() -> snapshotCodec.readResult(AnalysisType.CAREER_CONSULTATION, "  "))
                .isInstanceOf(InvalidSajuDataException.class);
    }

    @Test
    @DisplayName("유형과 맞지 않는 스냅샷은 저장 단계에서 거부한다")
    void shouldRejectSnapshotTypeMismatchOnWrite() {
        assertThatThrownBy(() -> snapshotCodec.writeResult(AnalysisType.SAJU,
                (AnalysisPayload.Result) snapshotCodec.readResult(AnalysisType.CAREER_CONSULTATION,
                        UnifiedAnalysisTestSupport.readFixture(
                                UnifiedAnalysisTestSupport.CONSULTATION_RESULT_FIXTURE))))
                .isInstanceOf(InvalidSajuDataException.class);
    }

    private Long save(AnalysisType type, String inputJson, String resultJson) {
        return sajuRepository.saveAndFlush(Saju.builder()
                .userId(owner.getId())
                .analysisType(type)
                .manse(manse)
                .inputJson(inputJson)
                .resultJson(resultJson)
                .build()).getId();
    }

    private String consultationInputJson() {
        return snapshotCodec.writeInput(AnalysisType.CAREER_CONSULTATION,
                AnalysisPayload.ConsultationInput.of(BIRTH_DATE, BIRTH_TIME, AnalysisTimeContext.from(clock)));
    }

    private String compatibilityInputJson() {
        return UnifiedAnalysisTestSupport.readFixture(
                UnifiedAnalysisTestSupport.COMPATIBILITY_INPUT_FIXTURE);
    }
}
