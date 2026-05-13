package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.domain.FiveElements;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.entity.ActionableStrategy;
import ssafy.SSAju.career.entity.AnalysisBreakdown;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.FiveElementsAnalysis;
import ssafy.SSAju.career.entity.TargetRoleAnalysis;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.util.AnalysisResponseBuilder;
import ssafy.SSAju.career.util.CompatibilityScoreCalculator;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.career.util.JobRoleAnalyzer;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.career.validator.SajuValidator;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.exception.FastAPITimeoutException;
import ssafy.SSAju.repository.ActionableKeywordRepository;
import ssafy.SSAju.repository.ActionableStrategyRepository;
import ssafy.SSAju.repository.AnalysisBreakdownRepository;
import ssafy.SSAju.repository.CautionRepository;
import ssafy.SSAju.repository.CompanyCompatibilityJdbcRepository;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;
import ssafy.SSAju.repository.ExpectedInterviewQuestionRepository;
import ssafy.SSAju.repository.FiveElementsAnalysisRepository;
import ssafy.SSAju.repository.LuckyDayRepository;
import ssafy.SSAju.repository.MonthlyForecastRepository;
import ssafy.SSAju.repository.RoleCompatibilityRepository;
import ssafy.SSAju.repository.TargetRoleAnalysisRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CompanyMatchingService 단위 테스트")
class CompanyMatchingServiceTest {

    @Mock private SajuDataService sajuDataService;
    @Mock private CompanyInfoService companyInfoService;
    @Mock private UserProfileProvider userProfileProvider;
    @Mock private SajuValidator sajuValidator;
    @Mock private TenGodCalculator tenGodCalculator;
    @Mock private HiddenStemCalculator hiddenStemCalculator;
    @Mock private CompatibilityScoreCalculator compatibilityScoreCalculator;
    @Mock private JobRoleAnalyzer jobRoleAnalyzer;
    @Mock private AnalysisResponseBuilder analysisResponseBuilder;
    @Mock private CompanyCompatibilityRepository companyCompatibilityRepository;
    @Mock private CompanyCompatibilityJdbcRepository companyCompatibilityJdbcRepository;
    @Mock private TargetRoleAnalysisRepository targetRoleAnalysisRepository;
    @Mock private FiveElementsAnalysisRepository fiveElementsAnalysisRepository;
    @Mock private AnalysisBreakdownRepository analysisBreakdownRepository;
    @Mock private ActionableStrategyRepository actionableStrategyRepository;
    @Mock private ExpectedInterviewQuestionRepository expectedInterviewQuestionRepository;
    @Mock private RoleCompatibilityRepository roleCompatibilityRepository;
    @Mock private ActionableKeywordRepository actionableKeywordRepository;
    @Mock private LuckyDayRepository luckyDayRepository;
    @Mock private MonthlyForecastRepository monthlyForecastRepository;
    @Mock private CautionRepository cautionRepository;
    @Mock private CompatibilityChildSaveService childSaveService;

    private CompanyMatchingService service;

    private static final LocalDate USER_BIRTH_DATE = LocalDate.of(1998, 5, 7);
    private static final LocalTime USER_BIRTH_TIME = LocalTime.of(14, 30);
    private static final LocalDate COMPANY_FOUNDING_DATE = LocalDate.of(2000, 4, 10);

    private static final FastAPIResponse MOCK_SAJU = new FastAPIResponse(
            List.of("庚", "甲", "己", "丁"),
            List.of("午", "戌", "未", "寅"),
            Map.of("木", 1, "火", 2, "土", 2, "金", 2, "水", 1),
            "庚午", "甲戌", "己未", "丁寅",
            "14:30", "1998-05-07", null
    );

    private static final UserProfile MOCK_USER_PROFILE = UserProfile.builder()
            .birthDate(USER_BIRTH_DATE)
            .birthTime(USER_BIRTH_TIME)
            .build();

    @BeforeEach
    void setUp() {
        service = new CompanyMatchingService(
                sajuDataService, companyInfoService, userProfileProvider, sajuValidator,
                tenGodCalculator, hiddenStemCalculator,
                compatibilityScoreCalculator, jobRoleAnalyzer, analysisResponseBuilder,
                companyCompatibilityRepository, companyCompatibilityJdbcRepository, childSaveService,
                targetRoleAnalysisRepository, fiveElementsAnalysisRepository,
                analysisBreakdownRepository, actionableStrategyRepository,
                actionableKeywordRepository, luckyDayRepository,
                expectedInterviewQuestionRepository, roleCompatibilityRepository,
                monthlyForecastRepository, cautionRepository
        );

        // AnalysisResponseBuilder 기본 mock 설정 (lenient - 테스트별 필요에 따라 재정의 가능)
        given(analysisResponseBuilder.buildFiveElementsData(any(), any()))
                .willReturn(new CompatibilityAnalysisData.FiveElementsInfo(Map.of(), Map.of(), "테스트 시너지"));
        given(analysisResponseBuilder.buildAnalysisBreakdown(anyInt()))
                .willReturn(new CompatibilityAnalysisData.ScoreBreakdown(80, 70, 75));
        given(analysisResponseBuilder.buildActionableStrategy(any()))
                .willReturn(new CompatibilityAnalysisData.StrategyInfo(List.of(), "약점 방어", List.of(), "09:00"));
        given(analysisResponseBuilder.buildInterviewQuestions(any())).willReturn(List.of());
        given(analysisResponseBuilder.buildRoleCompatibilities(any(), any())).willReturn(List.of());
        given(analysisResponseBuilder.buildMonthlyForecasts()).willReturn(List.of());
        given(analysisResponseBuilder.buildCautions(any(), any())).willReturn(List.of());
        given(analysisResponseBuilder.buildSummary(anyInt(), any())).willReturn("테스트 요약");
    }

    // ─────────────────────────────────────────
    // 정상 플로우: 신규 삽입
    // ─────────────────────────────────────────

    @Test
    @DisplayName("신규 요청 → INSERT IGNORE 성공, 자식 엔티티 저장, 응답 반환")
    void shouldSaveAllChildren_WhenNewCompatibilityInserted() {
        // Given
        CompatibilityRequest request = buildRequest(JobCategoryEnum.TECH_BACKEND);
        HiddenStems mockHiddenStems = new HiddenStems(
                Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                        "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));
        CompanyCompatibility savedEntity = buildCompatibility(MOCK_USER_PROFILE);

        given(userProfileProvider.findOrCreate(any(), any())).willReturn(MOCK_USER_PROFILE);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);

        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(new CompatibilityAnalysisData.RoleAnalysis(85, "시너지 텍스트", "경고 텍스트"));
        given(companyCompatibilityJdbcRepository.insertOrIgnore(any())).willReturn(1);
        given(companyCompatibilityRepository.findByUserProfile_IdAndCompanyNameAndTargetRoleCategory(
                any(), anyString(), any())).willReturn(Optional.of(savedEntity));

        // When
        CompatibilityResponse response = service.analyzeCompatibility(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.compatibilityScore()).isEqualTo(78);
        assertThat(response.requestContext().companyName()).isEqualTo("현대오토에버");
        verify(childSaveService).saveAllAndMarkCompleted(any(), any());
    }

    // ─────────────────────────────────────────
    // Race Condition: 이미 존재하는 결과 재사용
    // ─────────────────────────────────────────

    @Test
    @DisplayName("이미 존재하는 요청 → INSERT IGNORE 0 반환, 기존 DB 데이터 재사용")
    void shouldReturnExistingData_WhenDuplicateCompatibility() {
        // Given
        CompatibilityRequest request = buildRequest(JobCategoryEnum.TECH_BACKEND);
        HiddenStems mockHiddenStems = new HiddenStems(
                Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                        "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));
        CompanyCompatibility existingEntity = buildCompatibility(MOCK_USER_PROFILE);

        given(userProfileProvider.findOrCreate(any(), any())).willReturn(MOCK_USER_PROFILE);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);

        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(new CompatibilityAnalysisData.RoleAnalysis(85, "시너지", "경고"));
        given(companyCompatibilityJdbcRepository.insertOrIgnore(any())).willReturn(0);
        given(companyCompatibilityRepository.findByUserProfile_IdAndCompanyNameAndTargetRoleCategory(
                any(), anyString(), any())).willReturn(Optional.of(existingEntity));

        // When
        CompatibilityResponse response = service.analyzeCompatibility(request);

        // Then: 신규 저장 없음
        assertThat(response).isNotNull();
        verify(targetRoleAnalysisRepository, never()).save(any());
        // 캐시 재사용 경로에서도 4개 분석 필드가 정상적으로 채워지는지 검증 (버그 방지)
        assertThat(response.targetRoleAnalysis()).isNotNull();
        assertThat(response.targetRoleAnalysis().matchScore()).isEqualTo(85);
        assertThat(response.fiveElements()).isNotNull();
        assertThat(response.analysisBreakdown()).isNotNull();
        assertThat(response.actionableStrategy()).isNotNull();
    }

    // ─────────────────────────────────────────
    // 기업 설립 시간 미상 → 기본값 12:00 처리
    // ─────────────────────────────────────────

    @Test
    @DisplayName("기업 설립 시간 null → 12:00 기본값으로 FastAPI 호출")
    void shouldUseDefaultTime_WhenCompanyFoundingTimeNull() {
        // Given: companyFoundingTime을 null로 설정
        CompatibilityRequest request = new CompatibilityRequest(
                USER_BIRTH_DATE, USER_BIRTH_TIME,
                new CompatibilityRequest.TargetRoleRequest(JobCategoryEnum.TECH_BACKEND, "백엔드 개발자"),
                "현대오토에버", COMPANY_FOUNDING_DATE, null
        );
        HiddenStems mockHiddenStems = new HiddenStems(Map.of());
        CompanyCompatibility savedEntity = buildCompatibility(MOCK_USER_PROFILE);

        given(userProfileProvider.findOrCreate(any(), any())).willReturn(MOCK_USER_PROFILE);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);

        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(60);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(new CompatibilityAnalysisData.RoleAnalysis(60, "시너지", "경고"));
        given(companyCompatibilityJdbcRepository.insertOrIgnore(any())).willReturn(1);
        given(companyCompatibilityRepository.findByUserProfile_IdAndCompanyNameAndTargetRoleCategory(
                any(), anyString(), any())).willReturn(Optional.of(savedEntity));

        // When
        CompatibilityResponse response = service.analyzeCompatibility(request);

        // Then
        assertThat(response).isNotNull();
        // LocalTime.of(12, 0)으로 호출 2회 (사용자 + 기업)
        verify(sajuDataService).fetchSajuFromFastAPI(USER_BIRTH_DATE, USER_BIRTH_TIME);
        verify(sajuDataService).fetchSajuFromFastAPI(COMPANY_FOUNDING_DATE, LocalTime.of(12, 0));
    }

    // ─────────────────────────────────────────
    // 응답 구조 검증
    // ─────────────────────────────────────────

    @Test
    @DisplayName("응답 내 requestContext 필드가 요청 값과 일치")
    void shouldReturnCorrectRequestContext_InResponse() {
        // Given
        CompatibilityRequest request = buildRequest(JobCategoryEnum.FINANCE);
        HiddenStems mockHiddenStems = new HiddenStems(Map.of());
        CompanyCompatibility savedEntity = buildCompatibility(MOCK_USER_PROFILE, JobCategoryEnum.FINANCE);

        given(userProfileProvider.findOrCreate(any(), any())).willReturn(MOCK_USER_PROFILE);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);

        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(65);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(new CompatibilityAnalysisData.RoleAnalysis(70, "시너지", "경고"));
        given(companyCompatibilityJdbcRepository.insertOrIgnore(any())).willReturn(1);
        given(companyCompatibilityRepository.findByUserProfile_IdAndCompanyNameAndTargetRoleCategory(
                any(), anyString(), any())).willReturn(Optional.of(savedEntity));

        // When
        CompatibilityResponse response = service.analyzeCompatibility(request);

        // Then
        assertThat(response.requestContext().companyName()).isEqualTo("현대오토에버");
        assertThat(response.requestContext().targetRole().category()).isEqualTo(JobCategoryEnum.FINANCE);
    }

    // ─────────────────────────────────────────
    // FastAPI 오류 전파
    // ─────────────────────────────────────────

    @Test
    @DisplayName("FastAPI 타임아웃 → FastAPITimeoutException 전파")
    void shouldPropagateException_WhenFastAPIFails() {
        // Given
        CompatibilityRequest request = buildRequest(JobCategoryEnum.TECH_BACKEND);

        given(userProfileProvider.findOrCreate(any(), any())).willReturn(MOCK_USER_PROFILE);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any()))
                .willThrow(new FastAPITimeoutException("FastAPI 응답 타임아웃"));

        // When & Then
        assertThatThrownBy(() -> service.analyzeCompatibility(request))
                .isInstanceOf(FastAPITimeoutException.class);
    }

    // ─────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────

    private CompatibilityRequest buildRequest(JobCategoryEnum category) {
        return new CompatibilityRequest(
                USER_BIRTH_DATE, USER_BIRTH_TIME,
                new CompatibilityRequest.TargetRoleRequest(category, "개발자"),
                "현대오토에버", COMPANY_FOUNDING_DATE, LocalTime.of(12, 0)
        );
    }

    private CompanyCompatibility buildCompatibility(UserProfile profile) {
        return buildCompatibility(profile, JobCategoryEnum.TECH_BACKEND);
    }

    private CompanyCompatibility buildCompatibility(UserProfile profile, JobCategoryEnum category) {
        return CompanyCompatibility.builder()
                .userProfile(profile)
                .companyName("현대오토에버")
                .targetRoleCategory(category)
                .targetRoleDetailName("개발자")
                .compatibilityScore(78)
                .summary("테스트 요약")
                .build();
    }

    private TargetRoleAnalysis buildTargetRoleAnalysisEntity(CompanyCompatibility compatibility) {
        return TargetRoleAnalysis.builder()
                .companyCompatibility(compatibility)
                .matchScore(85)
                .synergy("시너지 텍스트")
                .warning("경고 텍스트")
                .build();
    }

    private FiveElementsAnalysis buildFiveElementsAnalysisEntity(CompanyCompatibility compatibility) {
        return FiveElementsAnalysis.builder()
                .companyCompatibility(compatibility)
                .userWood(1).userFire(2).userEarth(2).userMetal(2).userWater(1)
                .companyWood(2).companyFire(1).companyEarth(1).companyMetal(2).companyWater(2)
                .synergyDescription("균형 잡힌 오행 구조")
                .build();
    }

    private AnalysisBreakdown buildAnalysisBreakdownEntity(CompanyCompatibility compatibility) {
        return AnalysisBreakdown.builder()
                .companyCompatibility(compatibility)
                .characterMatch(83)
                .potentialSynergy(73)
                .longTermStability(78)
                .build();
    }

    private ActionableStrategy buildActionableStrategyEntity(CompanyCompatibility compatibility) {
        return ActionableStrategy.builder()
                .companyCompatibility(compatibility)
                .weaknessDefense("지속적 학습 의지를 강조하세요.")
                .preferredTime("오전 09:00 ~ 11:00")
                .build();
    }
}
