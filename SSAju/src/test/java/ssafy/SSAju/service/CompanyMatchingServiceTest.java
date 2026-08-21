package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import ssafy.SSAju.career.provider.UserProfileProvider;
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
import ssafy.SSAju.repository.CompanyCompatibilityRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * CompanyMatchingService 단위 테스트.
 *
 * <p>락 배치를 저장 단계로 좁히면서(더블체크락 제거, US5 후속 리팩토링) 사주 계산/AI 호출을
 * 다시 이 클래스로 합쳤다 — self-invocation 문제가 있는 건 저장 단계뿐이므로, 그 부분만
 * {@link CompanyCompatibilitySaveService}로 분리되어 있다. 그래서 이 테스트는 원래(US5 이전)
 * 구조와 비슷하게 전체 흐름을 검증하되, DB 저장 세부사항(재시도/재사용 판단)은
 * {@code CompanyCompatibilitySaveServiceTest}에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CompanyMatchingService 단위 테스트")
class CompanyMatchingServiceTest {

    @Mock private SajuDataService sajuDataService;
    @Mock private CompanyInfoService companyInfoService;
    @Mock private UserProfileProvider userProfileProvider;
    @Mock private SajuValidator sajuValidator;
    @Mock private HiddenStemCalculator hiddenStemCalculator;
    @Mock private CompatibilityScoreCalculator compatibilityScoreCalculator;
    @Mock private JobRoleAnalyzer jobRoleAnalyzer;
    @Mock private RoleCompatibilityCalculator roleCompatibilityCalculator;
    @Mock private AnalysisResponseBuilder analysisResponseBuilder;
    @Mock private CompanyMatchingOpenAICaller companyMatchingOpenAICaller;
    @Mock private CompanyCompatibilityRepository companyCompatibilityRepository;
    @Mock private CompatibilityChildReadService childReadService;
    @Mock private CompanyCompatibilitySaveService compatibilitySaveService;
    @Mock private UserRepository userRepository;
    @Mock private DailyApiUsageService dailyApiUsageService;

    private CompanyMatchingService service;

    /** 테스트 고정 날짜: 2026-05-27 KST → compatibilityMonth = 202605 */
    private static final int TEST_COMPATIBILITY_MONTH = 202605;
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-27T01:00:00Z"),  // UTC 01:00 = KST 10:00
            ZoneId.of("Asia/Seoul")
    );

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
        service = new CompanyMatchingService(
                sajuDataService, companyInfoService, userProfileProvider, sajuValidator,
                hiddenStemCalculator, compatibilityScoreCalculator, jobRoleAnalyzer, roleCompatibilityCalculator,
                analysisResponseBuilder, companyMatchingOpenAICaller,
                companyCompatibilityRepository, childReadService, compatibilitySaveService,
                userRepository, FIXED_CLOCK, dailyApiUsageService
        );
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(MOCK_USER));
        given(userProfileProvider.findOrCreate(any(), any())).willReturn(MOCK_USER_PROFILE);

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

        // 이번 달 캐시 기본값: 캐시 미스 (lenient)
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonthAndCompletedTrue(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.empty());
    }

    // ─────────────────────────────────────────
    // 정상 플로우: 신규 분석 → 저장 → 응답
    // ─────────────────────────────────────────

    @Test
    @DisplayName("신규 요청 → 저장 서비스 호출, childReadService로 응답 조립, 쿼터 1회 차감")
    void shouldSaveAndBuildResponse_WhenNewRequest() {
        // Given
        CompatibilityRequest request = buildRequest(JobCategoryEnum.TECH_BACKEND);
        HiddenStems mockHiddenStems = new HiddenStems(
                Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                        "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));
        CompanyCompatibility savedEntity = buildCompatibility(MOCK_USER_PROFILE);
        CompatibilityResponse expectedResponse = new CompatibilityResponse(
                savedEntity.getId(), null, 78, "테스트 요약",
                null, null, null, null, null, null, null, null
        );

        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(85);
        given(compatibilitySaveService.saveWithLock(any(), any()))
                .willReturn(new CompanyCompatibilitySaveService.SaveOutcome(savedEntity, true));
        given(childReadService.buildFromExisting(savedEntity, request)).willReturn(expectedResponse);

        // When
        CompatibilityResponse response = service.analyzeCompatibility(request, USER_ID);

        // Then
        assertThat(response).isEqualTo(expectedResponse);
        verify(compatibilitySaveService).saveWithLock(any(), any());
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
        verify(dailyApiUsageService, never()).restoreQuietly(any(), any());
        verify(dailyApiUsageService, never()).restoreQuietly(any(), any(), any());
    }

    @Test
    @DisplayName("AI 응답의 텍스트 필드가 저장 서비스에 전달되는 CompatibilityAnalysisData에 반영된다")
    void shouldPassAiNarrativeFields_ToSaveService() {
        // Given
        CompatibilityRequest request = buildRequest(JobCategoryEnum.TECH_BACKEND);
        HiddenStems mockHiddenStems = new HiddenStems(
                Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                        "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));
        CompanyCompatibility savedEntity = buildCompatibility(MOCK_USER_PROFILE);

        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(85);
        given(compatibilitySaveService.saveWithLock(any(), any()))
                .willReturn(new CompanyCompatibilitySaveService.SaveOutcome(savedEntity, true));

        // When
        service.analyzeCompatibility(request, USER_ID);

        // Then
        ArgumentCaptor<CompatibilityAnalysisData> dataCaptor = ArgumentCaptor.forClass(CompatibilityAnalysisData.class);
        ArgumentCaptor<CompanyCompatibility> rootCaptor = ArgumentCaptor.forClass(CompanyCompatibility.class);
        verify(compatibilitySaveService).saveWithLock(rootCaptor.capture(), dataCaptor.capture());

        assertThat(rootCaptor.getValue().getSummary()).isEqualTo(DEFAULT_NARRATIVE.summary());
        assertThat(dataCaptor.getValue().roleAnalysis().matchScore()).isEqualTo(85);
        assertThat(dataCaptor.getValue().roleAnalysis().synergy()).isEqualTo(DEFAULT_NARRATIVE.roleSynergy());
        assertThat(dataCaptor.getValue().roleAnalysis().warning()).isEqualTo(DEFAULT_NARRATIVE.roleWarning());
        verify(companyMatchingOpenAICaller).call(any());
    }

    // ─────────────────────────────────────────
    // 월별 캐시 히트: 이미 완료된 이번 달 분석 재사용
    // ─────────────────────────────────────────

    @Test
    @DisplayName("이번 달 completed=true 캐시 존재 → 외부 API 호출 없이 즉시 반환")
    void shouldReturnCachedResult_WhenThisMonthCompleted() {
        // Given
        CompatibilityRequest request = buildRequest(JobCategoryEnum.TECH_BACKEND);
        CompanyCompatibility completedEntity = buildCompatibility(MOCK_USER_PROFILE);
        completedEntity.assignResultJsonAndMarkCompleted(buildAnalysisData());
        CompatibilityResponse cachedResponse = new CompatibilityResponse(
                completedEntity.getId(), null, 78, "캐시 요약",
                null, null, null, null, null, null, null, null
        );

        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonthAndCompletedTrue(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.of(completedEntity));
        given(childReadService.buildFromExisting(completedEntity, request)).willReturn(cachedResponse);

        // When
        CompatibilityResponse response = service.analyzeCompatibility(request, USER_ID);

        // Then: 외부 API 호출 없음, 캐시 반환
        assertThat(response).isEqualTo(cachedResponse);
        verify(sajuDataService, never()).fetchSajuFromFastAPI(any(), any());
        verify(compatibilitySaveService, never()).saveWithLock(any(), any());
        verify(dailyApiUsageService, never()).checkAndIncrementDailyUsage(USER_ID);
        verify(companyMatchingOpenAICaller, never()).call(any());
    }

    // ─────────────────────────────────────────
    // 기업 설립 시간 미상 → 기본값 12:00 처리
    // ─────────────────────────────────────────

    @Test
    @DisplayName("기업 설립 시간 null → 12:00 기본값으로 FastAPI 호출")
    void shouldUseDefaultTime_WhenCompanyFoundingTimeNull() {
        // Given
        CompatibilityRequest request = new CompatibilityRequest(
                USER_BIRTH_DATE, USER_BIRTH_TIME,
                new CompatibilityRequest.TargetRoleRequest(JobCategoryEnum.TECH_BACKEND, "백엔드 개발자"),
                "현대오토에버", COMPANY_FOUNDING_DATE, null
        );
        HiddenStems mockHiddenStems = new HiddenStems(Map.of());
        CompanyCompatibility savedEntity = buildCompatibility(MOCK_USER_PROFILE);

        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(60);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(60);
        given(compatibilitySaveService.saveWithLock(any(), any()))
                .willReturn(new CompanyCompatibilitySaveService.SaveOutcome(savedEntity, true));

        // When
        service.analyzeCompatibility(request, USER_ID);

        // Then: 기본 시간(12:00)으로 FastAPI 호출
        verify(sajuDataService).fetchSajuFromFastAPI(USER_BIRTH_DATE, USER_BIRTH_TIME);
        verify(sajuDataService).fetchSajuFromFastAPI(COMPANY_FOUNDING_DATE, LocalTime.of(12, 0));
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
    }

    // ─────────────────────────────────────────
    // FastAPI 오류 전파
    // ─────────────────────────────────────────

    @Test
    @DisplayName("FastAPI 타임아웃 → FastAPITimeoutException 전파, 저장 서비스는 호출되지 않음")
    void shouldPropagateException_WhenFastAPIFails() {
        // Given
        CompatibilityRequest request = buildRequest(JobCategoryEnum.TECH_BACKEND);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any()))
                .willThrow(new FastAPITimeoutException("FastAPI 응답 타임아웃"));

        // When & Then
        assertThatThrownBy(() -> service.analyzeCompatibility(request, USER_ID))
                .isInstanceOf(FastAPITimeoutException.class);
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
        verify(dailyApiUsageService).restoreQuietly(eq(USER_ID), any(), any());
        verify(compatibilitySaveService, never()).saveWithLock(any(), any());
    }

    // ─────────────────────────────────────────
    // AI 실패/최종 저장 실패 시 쿼터 보상
    // ─────────────────────────────────────────

    @Test
    @DisplayName("AI 해설 생성 실패 → 쿼터 복원 후 원본 예외 전파")
    void shouldRestoreQuota_WhenAiNarrativeCallFails() {
        // Given
        CompatibilityRequest request = buildRequest(JobCategoryEnum.TECH_BACKEND);
        HiddenStems mockHiddenStems = new HiddenStems(
                Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                        "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));
        LocalDate usageDate = LocalDate.of(2026, 5, 27);
        OpenAIApiException aiFailure = new OpenAIApiException("AI 호출 실패");

        given(dailyApiUsageService.checkAndIncrementDailyUsage(USER_ID)).willReturn(usageDate);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(85);
        given(companyMatchingOpenAICaller.call(any())).willThrow(aiFailure);

        // When & Then
        assertThatThrownBy(() -> service.analyzeCompatibility(request, USER_ID))
                .isSameAs(aiFailure);
        verify(dailyApiUsageService).restoreQuietly(USER_ID, usageDate, aiFailure);
        verify(compatibilitySaveService, never()).saveWithLock(any(), any());
    }

    @Test
    @DisplayName("AI 해설 생성은 성공했지만 저장 서비스 실패 → 쿼터 복원 후 원본 예외 전파")
    void shouldRestoreQuota_WhenSaveServiceFails() {
        // Given
        CompatibilityRequest request = buildRequest(JobCategoryEnum.TECH_BACKEND);
        HiddenStems mockHiddenStems = new HiddenStems(
                Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                        "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));
        LocalDate usageDate = LocalDate.of(2026, 5, 27);
        DataAccessException saveFailure = new DataAccessException("DB 저장 실패");

        given(dailyApiUsageService.checkAndIncrementDailyUsage(USER_ID)).willReturn(usageDate);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(85);
        given(compatibilitySaveService.saveWithLock(any(), any())).willThrow(saveFailure);

        // When & Then
        assertThatThrownBy(() -> service.analyzeCompatibility(request, USER_ID))
                .isSameAs(saveFailure);
        verify(dailyApiUsageService).restoreQuietly(USER_ID, usageDate, saveFailure);
    }

    // ─────────────────────────────────────────
    // 따닥(동일 요청 동시 도착) → 락 안 재확인에서 남의 행 재사용 → 쿼터 보상
    // ─────────────────────────────────────────

    @Test
    @DisplayName("saveWithLock이 남의 행을 재사용(newlyCreated=false)했다면 예외 없이도 쿼터를 보상 복원한다")
    void shouldRestoreQuota_WhenSaveServiceReusedExistingRow() {
        // Given
        CompatibilityRequest request = buildRequest(JobCategoryEnum.TECH_BACKEND);
        HiddenStems mockHiddenStems = new HiddenStems(
                Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                        "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));
        LocalDate usageDate = LocalDate.of(2026, 5, 27);
        CompanyCompatibility winnerEntity = buildCompatibility(MOCK_USER_PROFILE);
        CompatibilityResponse winnerResponse = new CompatibilityResponse(
                winnerEntity.getId(), null, 78, "경쟁에서 이긴 스레드의 요약",
                null, null, null, null, null, null, null, null
        );

        given(dailyApiUsageService.checkAndIncrementDailyUsage(USER_ID)).willReturn(usageDate);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(85);
        // 이 스레드가 계산한 결과는 버려지고, 락 안 재확인에서 이미 완료된 다른 스레드의 행을 반환
        given(compatibilitySaveService.saveWithLock(any(), any()))
                .willReturn(new CompanyCompatibilitySaveService.SaveOutcome(winnerEntity, false));
        given(childReadService.buildFromExisting(winnerEntity, request)).willReturn(winnerResponse);

        // When
        CompatibilityResponse response = service.analyzeCompatibility(request, USER_ID);

        // Then: 예외는 없었지만(정상 반환), 이 요청은 새 값을 만들지 못했으므로 쿼터를 보상 복원해야 한다
        assertThat(response).isEqualTo(winnerResponse);
        verify(dailyApiUsageService).restoreQuietly(USER_ID, usageDate);
    }

    @Test
    @DisplayName("저장은 성공했는데 응답 조립이 실패해도 쿼터를 복원하지 않는다(이중 지급 방지)")
    void shouldNotRestoreQuota_WhenSaveSucceedsButResponseBuildingFails() {
        // Given: saveWithLock까지는 성공(DB에 행이 남음)하지만, 그 이후 응답 조립 단계에서 실패
        CompatibilityRequest request = buildRequest(JobCategoryEnum.TECH_BACKEND);
        HiddenStems mockHiddenStems = new HiddenStems(
                Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                        "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));
        LocalDate usageDate = LocalDate.of(2026, 5, 27);
        CompanyCompatibility savedEntity = buildCompatibility(MOCK_USER_PROFILE);
        DataAccessException buildFailure = new DataAccessException("completed=true인데 resultJson이 없음");

        given(dailyApiUsageService.checkAndIncrementDailyUsage(USER_ID)).willReturn(usageDate);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(85);
        given(compatibilitySaveService.saveWithLock(any(), any()))
                .willReturn(new CompanyCompatibilitySaveService.SaveOutcome(savedEntity, true));
        given(childReadService.buildFromExisting(savedEntity, request)).willThrow(buildFailure);

        // When & Then
        assertThatThrownBy(() -> service.analyzeCompatibility(request, USER_ID))
                .isSameAs(buildFailure);
        // 저장은 이미 성공했으므로(newlyCreated=true) 쿼터를 되돌려주면 안 된다 — 그러면
        // DB엔 값이 남아있는데 쿼터만 공짜로 생기는 이중 지급이 된다.
        verify(dailyApiUsageService, never()).restoreQuietly(any(), any());
        verify(dailyApiUsageService, never()).restoreQuietly(any(), any(), any());
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
                .user(MOCK_USER)
                .companyName("현대오토에버")
                .targetRoleCategory(category)
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
