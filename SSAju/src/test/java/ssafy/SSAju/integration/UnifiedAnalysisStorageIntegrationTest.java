package ssafy.SSAju.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.career.converter.AnalysisSnapshotCodec;
import ssafy.SSAju.career.converter.ManseDataConverter;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.AnalysisTimeContext;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.repository.ManseRepository;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 새 저장 구조(manse/saju)의 숫자 ID·단일 JSON·필수 FK·유일 제약을 실제 MySQL에서 검증한다.
 *
 * <p>운영 초기화/DDL SQL은 이 테스트에서 실행하지 않는다 — 전환 절차는 운영자 전용 작업이며
 * 여기서는 테스트 전용 DB의 신규 모델만 확인한다.
 */
@Testcontainers
@SpringBootTest
@DisplayName("통합 분석 저장 구조 통합 테스트 (MySQL)")
class UnifiedAnalysisStorageIntegrationTest {

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

    private final ManseDataConverter manseDataConverter = new ManseDataConverter();

    private User owner;
    private UserProfile profile;
    private Manse manse;

    @BeforeEach
    void setUp() {
        sajuRepository.deleteAllInBatch();
        manseRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();

        owner = userRepository.save(UnifiedAnalysisTestSupport.newUser("unified-storage@test.com"));
        profile = userProfileRepository.save(
                UnifiedAnalysisTestSupport.newProfile(LocalDate.of(1995, 3, 20), LocalTime.of(9, 15)));
        manse = manseRepository.saveAndFlush(Manse.builder()
                .userProfile(profile)
                .data(UnifiedAnalysisTestSupport.manseData())
                .fetchedAt(UnifiedAnalysisTestSupport.FIXED_INSTANT)
                .build());
    }

    @Test
    @DisplayName("만세력 data_json 한 컬럼에 전 필드가 왕복 저장된다")
    void shouldRoundTripEveryManseDataField() {
        // Given
        ManseData expected = UnifiedAnalysisTestSupport.manseData();

        // When
        ManseData reloaded = manseRepository.findById(manse.getId()).orElseThrow().getData();

        // Then
        assertThat(reloaded).isEqualTo(expected);
        assertThat(reloaded.solarCorrection()).isEqualTo(expected.solarCorrection());
        assertThat(reloaded.tenGodHiddenStemAnalysis()).isEqualTo(expected.tenGodHiddenStemAnalysis());
        assertThat(reloaded.heavenlyStems()).containsExactlyElementsOf(expected.heavenlyStems());
        assertThat(reloaded.earthlyBranches()).containsExactlyElementsOf(expected.earthlyBranches());
    }

    @Test
    @DisplayName("지원하지 않는 만세력 스키마 버전은 읽기 단계에서 거부된다")
    void shouldRejectUnsupportedManseSchemaVersion() {
        // Given
        String unsupported = UnifiedAnalysisTestSupport.readFixture(
                UnifiedAnalysisTestSupport.MANSE_DATA_FIXTURE).replace("\"schemaVersion\": 1", "\"schemaVersion\": 2");

        // When / Then
        assertThatThrownBy(() -> manseDataConverter.convertToEntityAttribute(unsupported))
                .isInstanceOf(InvalidSajuDataException.class);
    }

    @Test
    @DisplayName("필수값이 빠진 만세력 JSON은 읽기 단계에서 거부된다")
    void shouldRejectManseDataMissingRequiredValues() {
        // Given: 천간이 4개가 아닌 손상된 저장값
        String damaged = UnifiedAnalysisTestSupport.readFixture(
                        UnifiedAnalysisTestSupport.MANSE_DATA_FIXTURE)
                .replace("[\"庚\", \"甲\", \"己\", \"丁\"]", "[\"庚\", \"甲\", \"己\"]");

        // When / Then
        assertThatThrownBy(() -> manseDataConverter.convertToEntityAttribute(damaged))
                .isInstanceOf(InvalidSajuDataException.class);
    }

    @Test
    @DisplayName("같은 출생정보 프로필과 프로필당 만세력은 유일 제약으로 중복 저장되지 않는다")
    void shouldEnforceProfileAndManseUniqueConstraints() {
        // When / Then: 같은 생년월일·시각 프로필
        assertThatThrownBy(() -> userProfileRepository.saveAndFlush(
                UnifiedAnalysisTestSupport.newProfile(LocalDate.of(1995, 3, 20), LocalTime.of(9, 15))))
                .isInstanceOf(DataIntegrityViolationException.class);

        // When / Then: 같은 프로필의 두 번째 만세력
        assertThatThrownBy(() -> manseRepository.saveAndFlush(Manse.builder()
                .userProfile(profile)
                .data(UnifiedAnalysisTestSupport.manseData())
                .fetchedAt(UnifiedAnalysisTestSupport.FIXED_INSTANT)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("모든 유형의 분석은 만세력 참조가 없으면 저장할 수 없다")
    void shouldRequireManseForEveryAnalysisType() {
        for (AnalysisType type : AnalysisType.values()) {
            assertThatThrownBy(() -> Saju.builder()
                    .userId(owner.getId())
                    .analysisType(type)
                    .manse(null)
                    .inputJson(sajuInputJson())
                    .resultJson(sajuResultJson())
                    .build())
                    .isInstanceOf(InvalidSajuDataException.class);
        }
    }

    @Test
    @DisplayName("같은 입력을 반복 저장해도 각각 다른 숫자 ID로 남는다")
    void shouldAssignNewNumericIdForEveryIdenticalRequest() {
        // Given
        String inputJson = sajuInputJson();
        String resultJson = sajuResultJson();

        // When
        Long firstId = saveSaju(AnalysisType.SAJU, inputJson, resultJson);
        Long secondId = saveSaju(AnalysisType.SAJU, inputJson, resultJson);
        Long thirdId = saveSaju(AnalysisType.SAJU, inputJson, resultJson);

        // Then
        assertThat(firstId).isNotNull().isInstanceOf(Long.class);
        assertThat(secondId).isGreaterThan(firstId);
        assertThat(thirdId).isGreaterThan(secondId);
        assertThat(sajuRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("세 유형의 결과 스냅샷이 유형별 record로 왕복된다")
    void shouldRoundTripResultSnapshotForEveryType() {
        // Given
        String consultationJson = UnifiedAnalysisTestSupport.readFixture(
                UnifiedAnalysisTestSupport.CONSULTATION_RESULT_FIXTURE);
        String compatibilityJson = UnifiedAnalysisTestSupport.readFixture(
                UnifiedAnalysisTestSupport.COMPATIBILITY_RESULT_FIXTURE);

        // When
        Long sajuId = saveSaju(AnalysisType.SAJU, sajuInputJson(), sajuResultJson());
        Long consultationId = saveSaju(AnalysisType.CAREER_CONSULTATION, sajuInputJson(), consultationJson);
        Long compatibilityId = saveSaju(AnalysisType.COMPANY_COMPATIBILITY, sajuInputJson(), compatibilityJson);

        // Then
        assertThat(snapshotCodec.readResult(AnalysisType.SAJU, resultJsonOf(sajuId)))
                .isInstanceOf(AnalysisPayload.SajuResultData.class);
        assertThat(snapshotCodec.readResult(AnalysisType.CAREER_CONSULTATION, resultJsonOf(consultationId)))
                .isInstanceOf(AnalysisPayload.ConsultationResultData.class);
        assertThat(snapshotCodec.readResult(AnalysisType.COMPANY_COMPATIBILITY, resultJsonOf(compatibilityId)))
                .isInstanceOf(AnalysisPayload.CompatibilityResultData.class);

        AnalysisPayload.Input input = snapshotCodec.readInput(AnalysisType.SAJU, inputJsonOf(sajuId));
        assertThat(input).isInstanceOf(AnalysisPayload.SajuInput.class);
        assertThat(((AnalysisPayload.SajuInput) input).birthDate()).isEqualTo(LocalDate.of(1995, 3, 20));
        assertThat(((AnalysisPayload.SajuInput) input).birthTime()).isEqualTo(LocalTime.of(9, 15));
    }

    @Test
    @DisplayName("사용자 기본 출생정보 참조는 비어 있을 수 있고 여러 사용자가 같은 프로필을 가리킬 수 있다")
    void shouldAllowNullAndSharedDefaultProfileReference() {
        // Given
        User second = userRepository.save(UnifiedAnalysisTestSupport.newUser("unified-storage-2@test.com"));

        // Then: 기본값은 비어 있다
        assertThat(userRepository.findById(owner.getId()).orElseThrow().getUserProfile()).isNull();

        // When: 두 사용자가 같은 공유 프로필을 기본값으로 가리킨다
        owner.changeDefaultProfile(profile);
        second.changeDefaultProfile(profile);
        userRepository.saveAndFlush(owner);
        userRepository.saveAndFlush(second);

        // Then
        assertThat(userRepository.findById(owner.getId()).orElseThrow().getUserProfile().getId())
                .isEqualTo(profile.getId());
        assertThat(userRepository.findById(second.getId()).orElseThrow().getUserProfile().getId())
                .isEqualTo(profile.getId());
    }

    private Long saveSaju(AnalysisType type, String inputJson, String resultJson) {
        return sajuRepository.saveAndFlush(Saju.builder()
                .userId(owner.getId())
                .analysisType(type)
                .manse(manse)
                .inputJson(inputJson)
                .resultJson(resultJson)
                .build()).getId();
    }

    private String resultJsonOf(Long sajuId) {
        return sajuRepository.findById(sajuId).orElseThrow().getResultJson();
    }

    private String inputJsonOf(Long sajuId) {
        return sajuRepository.findById(sajuId).orElseThrow().getInputJson();
    }

    private String sajuInputJson() {
        AnalysisTimeContext time = AnalysisTimeContext.from(UnifiedAnalysisTestSupport.fixedClock());
        return snapshotCodec.writeInput(AnalysisType.SAJU,
                AnalysisPayload.SajuInput.of(LocalDate.of(1995, 3, 20), LocalTime.of(9, 15), time));
    }

    private String sajuResultJson() {
        return UnifiedAnalysisTestSupport.readFixture(UnifiedAnalysisTestSupport.SAJU_RESULT_FIXTURE);
    }
}
