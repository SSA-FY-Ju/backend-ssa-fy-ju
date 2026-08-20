package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ssafy.SSAju.career.caller.CompanyMatchingOpenAICaller;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.domain.FiveElements;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.util.AnalysisResponseBuilder;
import ssafy.SSAju.career.util.CompatibilityScoreCalculator;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.career.util.JobRoleAnalyzer;
import ssafy.SSAju.career.util.RoleCompatibilityCalculator;
import ssafy.SSAju.career.validator.SajuValidator;
import ssafy.SSAju.dto.external.CompatibilityNarrativeResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.exception.FastAPITimeoutException;
import ssafy.SSAju.exception.OpenAIApiException;
import ssafy.SSAju.repository.CompanyCompatibilityJdbcRepository;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;

import java.time.Instant;
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
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * CompanyCompatibilityLockedAnalysisService 단위 테스트 (US5, T035).
 *
 * <p>{@code CompanyMatchingService}가 락 없이 처리하는 1차 캐시 조회를 통과한 이후의 구간
 * (락 안 더블체크 캐시 확인 → 쿼터 차감 → 사주 계산/AI 호출/저장/보상)을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CompanyCompatibilityLockedAnalysisService 단위 테스트")
class CompanyCompatibilityLockedAnalysisServiceTest {

    @Mock private SajuDataService sajuDataService;
    @Mock private CompanyInfoService companyInfoService;
    @Mock private SajuValidator sajuValidator;
    @Mock private HiddenStemCalculator hiddenStemCalculator;
    @Mock private CompatibilityScoreCalculator compatibilityScoreCalculator;
    @Mock private JobRoleAnalyzer jobRoleAnalyzer;
    @Mock private RoleCompatibilityCalculator roleCompatibilityCalculator;
    @Mock private AnalysisResponseBuilder analysisResponseBuilder;
    @Mock private CompanyMatchingOpenAICaller companyMatchingOpenAICaller;
    @Mock private CompanyCompatibilityRepository companyCompatibilityRepository;
    @Mock private CompanyCompatibilityJdbcRepository companyCompatibilityJdbcRepository;
    @Mock private CompatibilityChildSaveService childSaveService;
    @Mock private CompatibilityChildReadService childReadService;
    @Mock private DailyApiUsageService dailyApiUsageService;

    private CompanyCompatibilityLockedAnalysisService service;

    private static final int TEST_COMPATIBILITY_MONTH = 202605;
    private static final Long USER_ID = 1L;
    private static final User MOCK_USER = User.builder()
            .email("test@test.com")
            .passwordHash("hash")
            .name("테스트")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .termsAgreedAt(Instant.now())
            .privacyAgreedAt(Instant.now())
            .build();

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

    private static final CompatibilityNarrativeResponse DEFAULT_NARRATIVE = new CompatibilityNarrativeResponse(
            "AI 요약", "AI 시너지", "AI 경고", "AI 오행 시너지", "AI 약점 방어",
            List.of(new CompatibilityNarrativeResponse.InterviewQuestion("AI 질문", "AI 의도")),
            "AI 전문가 사유", "AI 리드 사유",
            List.of(1, 2, 3, 4, 5).stream()
                    .map(month -> new CompatibilityNarrativeResponse.MonthlyAdvice(month, "AI " + month + "월"))
                    .toList(),
            List.of("AI 주의사항")
    );

    @BeforeEach
    void setUp() {
        service = new CompanyCompatibilityLockedAnalysisService(
                sajuDataService, companyInfoService, sajuValidator, hiddenStemCalculator,
                compatibilityScoreCalculator, jobRoleAnalyzer, roleCompatibilityCalculator,
                analysisResponseBuilder, companyMatchingOpenAICaller,
                companyCompatibilityRepository, companyCompatibilityJdbcRepository,
                childSaveService, childReadService, dailyApiUsageService
        );

        given(companyMatchingOpenAICaller.call(any())).willReturn(DEFAULT_NARRATIVE);
        given(analysisResponseBuilder.buildFiveElementsData(any(), any(), any()))
                .willReturn(new CompatibilityAnalysisData.FiveElementsInfo(Map.of(), Map.of(), "테스트 시너지"));
        given(analysisResponseBuilder.buildAnalysisBreakdown(anyInt()))
                .willReturn(new CompatibilityAnalysisData.ScoreBreakdown(80, 70, 75));
        given(analysisResponseBuilder.buildActionableStrategy(any(), any()))
                .willReturn(new CompatibilityAnalysisData.StrategyInfo(List.of(), "약점 방어", List.of(), "09:00"));
        given(analysisResponseBuilder.buildInterviewQuestions(any())).willReturn(List.of());
        given(analysisResponseBuilder.buildRoleCompatibilities(any(), anyInt(), anyInt(), any(), any()))
                .willReturn(List.of());
        given(analysisResponseBuilder.buildMonthlyForecasts(any(), any())).willReturn(List.of());

        // 더블체크 캐시 기본값: 미스
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.empty());
    }

    @Test
    @DisplayName("락 안 더블체크 캐시 히트 → 외부 API 호출 없이 즉시 반환, 쿼터 미차감")
    void shouldReturnCachedResult_WhenDoubleCheckCacheHit() {
        CompatibilityRequest request = buildRequest();
        CompanyCompatibility completedEntity = buildCompatibility();
        completedEntity.assignResultJsonAndMarkCompleted(buildAnalysisData());
        CompatibilityResponse cachedResponse = new CompatibilityResponse(
                completedEntity.getId(), null, 78, "캐시 요약",
                null, null, null, null, null, null, null, null
        );

        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.of(completedEntity));
        given(childReadService.buildFromExisting(completedEntity, request)).willReturn(cachedResponse);

        CompatibilityResponse response = service.analyzeWithLock(
                request, USER_ID, MOCK_USER, MOCK_USER_PROFILE, TEST_COMPATIBILITY_MONTH, USER_BIRTH_TIME);

        assertThat(response).isEqualTo(cachedResponse);
        verify(sajuDataService, never()).fetchSajuFromFastAPI(any(), any());
        verify(dailyApiUsageService, never()).checkAndIncrementDailyUsage(any());
        verify(companyMatchingOpenAICaller, never()).call(any());
    }

    @Test
    @DisplayName("더블체크 캐시 미스 → 쿼터 1회 차감, 사주 계산/AI 호출/저장 후 응답 반환")
    void shouldAnalyzeAndPersist_WhenDoubleCheckCacheMiss() {
        CompatibilityRequest request = buildRequest();
        HiddenStems mockHiddenStems = new HiddenStems(
                Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                        "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));
        CompanyCompatibility savedEntity = buildCompatibility();

        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString())).willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class))).willReturn(85);
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.empty())        // 더블체크: 미스
                .willReturn(Optional.of(savedEntity)); // insert 후 재조회

        CompatibilityResponse response = service.analyzeWithLock(
                request, USER_ID, MOCK_USER, MOCK_USER_PROFILE, TEST_COMPATIBILITY_MONTH, USER_BIRTH_TIME);

        assertThat(response).isNotNull();
        assertThat(response.compatibilityScore()).isEqualTo(78);
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
        verify(dailyApiUsageService, never()).restoreDailyUsage(any(), any());
        verify(companyCompatibilityJdbcRepository).insert(any());
        verify(childSaveService).saveAllAndMarkCompleted(any(), any());
    }

    @Test
    @DisplayName("AI 응답의 텍스트 필드가 최종 응답에 그대로 반영된다")
    void shouldReflectAiNarrativeFields_InFinalResponse() {
        CompatibilityRequest request = buildRequest();
        HiddenStems mockHiddenStems = new HiddenStems(Map.of());
        CompanyCompatibility savedEntity = CompanyCompatibility.builder()
                .userProfile(MOCK_USER_PROFILE)
                .user(MOCK_USER)
                .companyName("현대오토에버")
                .targetRoleCategory(JobCategoryEnum.TECH_BACKEND)
                .targetRoleDetailName("개발자")
                .compatibilityScore(78)
                .summary(DEFAULT_NARRATIVE.summary())
                .compatibilityMonth(TEST_COMPATIBILITY_MONTH)
                .build();

        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString())).willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class))).willReturn(85);
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(savedEntity));

        CompatibilityResponse response = service.analyzeWithLock(
                request, USER_ID, MOCK_USER, MOCK_USER_PROFILE, TEST_COMPATIBILITY_MONTH, USER_BIRTH_TIME);

        assertThat(response.summary()).isEqualTo(DEFAULT_NARRATIVE.summary());
        assertThat(response.targetRoleAnalysis().matchScore()).isEqualTo(85);
        assertThat(response.targetRoleAnalysis().synergy()).isEqualTo(DEFAULT_NARRATIVE.roleSynergy());
        assertThat(response.targetRoleAnalysis().warning()).isEqualTo(DEFAULT_NARRATIVE.roleWarning());
        verify(companyMatchingOpenAICaller).call(any());
    }

    @Test
    @DisplayName("기업 설립 시간 null → 12:00 기본값으로 FastAPI 호출")
    void shouldUseDefaultTime_WhenCompanyFoundingTimeNull() {
        CompatibilityRequest request = new CompatibilityRequest(
                USER_BIRTH_DATE, USER_BIRTH_TIME,
                new CompatibilityRequest.TargetRoleRequest(JobCategoryEnum.TECH_BACKEND, "백엔드 개발자"),
                "현대오토에버", COMPANY_FOUNDING_DATE, null
        );
        HiddenStems mockHiddenStems = new HiddenStems(Map.of());
        CompanyCompatibility savedEntity = buildCompatibility();

        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString())).willReturn(60);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class))).willReturn(60);
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(savedEntity));

        CompatibilityResponse response = service.analyzeWithLock(
                request, USER_ID, MOCK_USER, MOCK_USER_PROFILE, TEST_COMPATIBILITY_MONTH, USER_BIRTH_TIME);

        assertThat(response).isNotNull();
        verify(sajuDataService).fetchSajuFromFastAPI(USER_BIRTH_DATE, USER_BIRTH_TIME);
        verify(sajuDataService).fetchSajuFromFastAPI(COMPANY_FOUNDING_DATE, LocalTime.of(12, 0));
    }

    @Test
    @DisplayName("FastAPI 실패 → 쿼터 복원 후 원본 예외 전파")
    void shouldRestoreQuota_WhenFastApiFails() {
        CompatibilityRequest request = buildRequest();
        LocalDate usageDate = LocalDate.of(2026, 5, 27);
        given(dailyApiUsageService.checkAndIncrementDailyUsage(USER_ID)).willReturn(usageDate);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any()))
                .willThrow(new FastAPITimeoutException("FastAPI 응답 타임아웃"));

        assertThatThrownBy(() -> service.analyzeWithLock(
                request, USER_ID, MOCK_USER, MOCK_USER_PROFILE, TEST_COMPATIBILITY_MONTH, USER_BIRTH_TIME))
                .isInstanceOf(FastAPITimeoutException.class);

        verify(dailyApiUsageService).restoreDailyUsage(USER_ID, usageDate);
    }

    @Test
    @DisplayName("AI 해설 생성 실패 → 쿼터 복원 후 원본 예외 전파")
    void shouldRestoreQuota_WhenAiNarrativeCallFails() {
        CompatibilityRequest request = buildRequest();
        HiddenStems mockHiddenStems = new HiddenStems(
                Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                        "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));
        LocalDate usageDate = LocalDate.of(2026, 5, 27);
        OpenAIApiException aiFailure = new OpenAIApiException("AI 호출 실패");

        given(dailyApiUsageService.checkAndIncrementDailyUsage(USER_ID)).willReturn(usageDate);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString())).willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class))).willReturn(85);
        given(companyMatchingOpenAICaller.call(any())).willThrow(aiFailure);

        assertThatThrownBy(() -> service.analyzeWithLock(
                request, USER_ID, MOCK_USER, MOCK_USER_PROFILE, TEST_COMPATIBILITY_MONTH, USER_BIRTH_TIME))
                .isSameAs(aiFailure);
        verify(dailyApiUsageService).restoreDailyUsage(USER_ID, usageDate);
        verify(childSaveService, never()).saveAllAndMarkCompleted(any(), any());
    }

    @Test
    @DisplayName("AI 해설 생성은 성공했지만 최종 저장 실패 → 쿼터 복원 후 원본 예외 전파")
    void shouldRestoreQuota_WhenFinalSaveFails() {
        CompatibilityRequest request = buildRequest();
        HiddenStems mockHiddenStems = new HiddenStems(
                Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                        "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));
        CompanyCompatibility savedEntity = buildCompatibility();
        LocalDate usageDate = LocalDate.of(2026, 5, 27);
        DataAccessException saveFailure = new DataAccessException("DB 저장 실패");

        given(dailyApiUsageService.checkAndIncrementDailyUsage(USER_ID)).willReturn(usageDate);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString())).willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class))).willReturn(85);
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(savedEntity));
        org.mockito.BDDMockito.willThrow(saveFailure)
                .given(childSaveService).saveAllAndMarkCompleted(any(), any());

        assertThatThrownBy(() -> service.analyzeWithLock(
                request, USER_ID, MOCK_USER, MOCK_USER_PROFILE, TEST_COMPATIBILITY_MONTH, USER_BIRTH_TIME))
                .isSameAs(saveFailure);
        verify(dailyApiUsageService).restoreDailyUsage(USER_ID, usageDate);
        // insert까지 성공한 completed=false 행을 남겨두면 재시도가 UNIQUE 위반으로 영구히 막히므로
        // 보상 삭제로 정리되어야 한다(회귀 검증).
        verify(companyCompatibilityRepository).delete(savedEntity);
    }

    private CompatibilityRequest buildRequest() {
        return new CompatibilityRequest(
                USER_BIRTH_DATE, USER_BIRTH_TIME,
                new CompatibilityRequest.TargetRoleRequest(JobCategoryEnum.TECH_BACKEND, "개발자"),
                "현대오토에버", COMPANY_FOUNDING_DATE, LocalTime.of(12, 0)
        );
    }

    private CompanyCompatibility buildCompatibility() {
        return CompanyCompatibility.builder()
                .userProfile(MOCK_USER_PROFILE)
                .user(MOCK_USER)
                .companyName("현대오토에버")
                .targetRoleCategory(JobCategoryEnum.TECH_BACKEND)
                .targetRoleDetailName("개발자")
                .compatibilityScore(78)
                .summary("테스트 요약")
                .compatibilityMonth(TEST_COMPATIBILITY_MONTH)
                .build();
    }

    private CompatibilityAnalysisData buildAnalysisData() {
        return new CompatibilityAnalysisData(
                new CompatibilityAnalysisData.RoleAnalysis(80, "시너지", "주의"),
                new CompatibilityAnalysisData.FiveElementsInfo(Map.of(), Map.of(), "오행 시너지"),
                new CompatibilityAnalysisData.ScoreBreakdown(70, 70, 70),
                new CompatibilityAnalysisData.StrategyInfo(List.of(), "약점 보완", List.of(), "오전"),
                List.of(), List.of(), List.of(), List.of()
        );
    }
}
