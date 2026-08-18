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
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.util.AnalysisResponseBuilder;
import ssafy.SSAju.career.util.CompatibilityScoreCalculator;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.career.util.JobRoleAnalyzer;
import ssafy.SSAju.career.util.RoleCompatibilityCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
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
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.service.DailyApiUsageService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
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
    @Mock private RoleCompatibilityCalculator roleCompatibilityCalculator;
    @Mock private AnalysisResponseBuilder analysisResponseBuilder;
    @Mock private CompanyMatchingOpenAICaller companyMatchingOpenAICaller;
    @Mock private CompanyCompatibilityRepository companyCompatibilityRepository;
    @Mock private CompanyCompatibilityJdbcRepository companyCompatibilityJdbcRepository;
    @Mock private CompatibilityChildSaveService childSaveService;
    @Mock private CompatibilityChildReadService childReadService;
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
                tenGodCalculator, hiddenStemCalculator,
                compatibilityScoreCalculator, jobRoleAnalyzer, roleCompatibilityCalculator,
                analysisResponseBuilder, companyMatchingOpenAICaller,
                companyCompatibilityRepository, companyCompatibilityJdbcRepository,
                childSaveService, childReadService, userRepository,
                FIXED_CLOCK, dailyApiUsageService
        );
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(MOCK_USER));

        // AI 응답 기본 mock 설정 (lenient)
        given(companyMatchingOpenAICaller.call(any())).willReturn(DEFAULT_NARRATIVE);

        // AnalysisResponseBuilder 기본 mock 설정 (lenient)
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
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.empty());
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
                .willReturn(85);
        given(companyCompatibilityJdbcRepository.insertOrIgnore(any())).willReturn(1);

        // INSERT 후 재조회 mock (이번 달로 조회)
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.empty())       // 1차 캐시 조회: 미스
                .willReturn(Optional.of(savedEntity)); // 2차 INSERT 후 재조회: 성공

        // When
        CompatibilityResponse response = service.analyzeCompatibility(request, USER_ID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.compatibilityScore()).isEqualTo(78);
        assertThat(response.requestContext().companyName()).isEqualTo("현대오토에버");
        verify(childSaveService).saveAllAndMarkCompleted(any(), any());
        // 캐시 미스 → FastAPI 호출 직전 차감: 1회 필수
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
        // 저장까지 성공한 정상 흐름 → 쿼터 복원은 절대 호출되면 안 됨(이중 지급 방지 회귀 검증)
        verify(dailyApiUsageService, never()).restoreDailyUsage(any(), any());
    }

    // ─────────────────────────────────────────
    // AI 해설 응답이 최종 응답에 그대로 반영되는지 (US1, T003)
    // ─────────────────────────────────────────

    @Test
    @DisplayName("AI 응답의 텍스트 필드가 최종 응답에 그대로 반영된다")
    void shouldReflectAiNarrativeFields_InFinalResponse() {
        // Given
        CompatibilityRequest request = buildRequest(JobCategoryEnum.TECH_BACKEND);
        HiddenStems mockHiddenStems = new HiddenStems(
                Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                        "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));
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

        given(userProfileProvider.findOrCreate(any(), any())).willReturn(MOCK_USER_PROFILE);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(85);
        given(companyCompatibilityJdbcRepository.insertOrIgnore(any())).willReturn(1);
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(savedEntity));

        // When
        CompatibilityResponse response = service.analyzeCompatibility(request, USER_ID);

        // Then: summary는 root 엔티티를 거쳐 저장/반환, synergy/warning/matchScore는 Service가 직접 조립
        assertThat(response.summary()).isEqualTo(DEFAULT_NARRATIVE.summary());
        assertThat(response.targetRoleAnalysis().matchScore()).isEqualTo(85);
        assertThat(response.targetRoleAnalysis().synergy()).isEqualTo(DEFAULT_NARRATIVE.roleSynergy());
        assertThat(response.targetRoleAnalysis().warning()).isEqualTo(DEFAULT_NARRATIVE.roleWarning());
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

        given(userProfileProvider.findOrCreate(any(), any())).willReturn(MOCK_USER_PROFILE);
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.of(completedEntity));
        given(childReadService.buildFromExisting(completedEntity, request)).willReturn(cachedResponse);

        // When
        CompatibilityResponse response = service.analyzeCompatibility(request, USER_ID);

        // Then: 외부 API 호출 없음, 캐시 반환
        assertThat(response).isEqualTo(cachedResponse);
        verify(sajuDataService, never()).fetchSajuFromFastAPI(any(), any());
        verify(childSaveService, never()).saveAllAndMarkCompleted(any(), any());
        // completed=true 캐시 히트 → 외부 API 미호출 경로: 차감 없음
        verify(dailyApiUsageService, never()).checkAndIncrementDailyUsage(USER_ID);
        // 캐시 히트 → AI 해설 호출 없음
        verify(companyMatchingOpenAICaller, never()).call(any());
    }

    // ─────────────────────────────────────────
    // Race Condition: INSERT 후 동시 요청이 먼저 삽입한 경우
    // ─────────────────────────────────────────

    @Test
    @DisplayName("INSERT IGNORE 0 + completed=false → 현재 계산 결과로 응답")
    void shouldReturnCalculatedResult_WhenInsertIgnoredAndNotCompleted() {
        // Given
        CompatibilityRequest request = buildRequest(JobCategoryEnum.TECH_BACKEND);
        HiddenStems mockHiddenStems = new HiddenStems(
                Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                        "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));
        CompanyCompatibility existingEntity = buildCompatibility(MOCK_USER_PROFILE); // completed=false

        given(userProfileProvider.findOrCreate(any(), any())).willReturn(MOCK_USER_PROFILE);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(85);
        given(companyCompatibilityJdbcRepository.insertOrIgnore(any())).willReturn(0);
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.empty())        // 1차 캐시 조회: 미스
                .willReturn(Optional.of(existingEntity)); // 2차 INSERT IGNORE 후 재조회

        // When
        CompatibilityResponse response = service.analyzeCompatibility(request, USER_ID);

        // Then: 현재 계산 결과로 응답, childSaveService 호출 없음
        assertThat(response).isNotNull();
        verify(childSaveService, never()).saveAllAndMarkCompleted(any(), any());
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
    }

    // ─────────────────────────────────────────
    // Race Condition: INSERT IGNORE 0 + completed=true
    // ─────────────────────────────────────────

    @Test
    @DisplayName("INSERT IGNORE 0 + completed=true → childReadService.buildFromExisting 호출")
    void shouldUseCachedResult_WhenInsertedZeroAndCompleted() {
        // Given
        CompatibilityRequest request = buildRequest(JobCategoryEnum.TECH_BACKEND);
        HiddenStems mockHiddenStems = new HiddenStems(
                Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                        "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));
        CompanyCompatibility completedEntity = buildCompatibility(MOCK_USER_PROFILE);
        completedEntity.assignResultJsonAndMarkCompleted(buildAnalysisData());
        CompatibilityResponse cachedResponse = new CompatibilityResponse(
                completedEntity.getId(), null, 78, "캐시 요약",
                null, null, null, null, null, null, null, null
        );

        given(userProfileProvider.findOrCreate(any(), any())).willReturn(MOCK_USER_PROFILE);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(85);
        given(companyCompatibilityJdbcRepository.insertOrIgnore(any())).willReturn(0);
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.empty())           // 1차 캐시 조회: 미스
                .willReturn(Optional.of(completedEntity)); // 2차 INSERT IGNORE 후 재조회
        given(childReadService.buildFromExisting(completedEntity, request)).willReturn(cachedResponse);

        // When
        CompatibilityResponse response = service.analyzeCompatibility(request, USER_ID);

        // Then
        assertThat(response).isEqualTo(cachedResponse);
        verify(childSaveService, never()).saveAllAndMarkCompleted(any(), any());
        verify(childReadService).buildFromExisting(completedEntity, request);
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
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

        given(userProfileProvider.findOrCreate(any(), any())).willReturn(MOCK_USER_PROFILE);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(60);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(60);
        given(companyCompatibilityJdbcRepository.insertOrIgnore(any())).willReturn(1);
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(savedEntity));

        // When
        CompatibilityResponse response = service.analyzeCompatibility(request, USER_ID);

        // Then: 기본 시간(12:00)으로 FastAPI 호출
        assertThat(response).isNotNull();
        verify(sajuDataService).fetchSajuFromFastAPI(USER_BIRTH_DATE, USER_BIRTH_TIME);
        verify(sajuDataService).fetchSajuFromFastAPI(COMPANY_FOUNDING_DATE, LocalTime.of(12, 0));
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
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
        assertThatThrownBy(() -> service.analyzeCompatibility(request, USER_ID))
                .isInstanceOf(FastAPITimeoutException.class);
        // 캐시 미스 → charge 후 FastAPI 실패: 차감은 이미 발생
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
    }

    // ─────────────────────────────────────────
    // AI 실패/최종 저장 실패 시 쿼터 보상 (US3, T015)
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

        given(userProfileProvider.findOrCreate(any(), any())).willReturn(MOCK_USER_PROFILE);
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
        verify(dailyApiUsageService).restoreDailyUsage(USER_ID, usageDate);
        verify(childSaveService, never()).saveAllAndMarkCompleted(any(), any());
    }

    @Test
    @DisplayName("AI 해설 생성은 성공했지만 최종 저장 실패 → 쿼터 복원 후 원본 예외 전파")
    void shouldRestoreQuota_WhenFinalSaveFails() {
        // Given
        CompatibilityRequest request = buildRequest(JobCategoryEnum.TECH_BACKEND);
        HiddenStems mockHiddenStems = new HiddenStems(
                Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                        "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));
        CompanyCompatibility savedEntity = buildCompatibility(MOCK_USER_PROFILE);
        LocalDate usageDate = LocalDate.of(2026, 5, 27);
        DataAccessException saveFailure = new DataAccessException("DB 저장 실패");

        given(userProfileProvider.findOrCreate(any(), any())).willReturn(MOCK_USER_PROFILE);
        given(dailyApiUsageService.checkAndIncrementDailyUsage(USER_ID)).willReturn(usageDate);
        given(sajuDataService.fetchSajuFromFastAPI(any(), any())).willReturn(MOCK_SAJU);
        given(hiddenStemCalculator.calculate(any())).willReturn(mockHiddenStems);
        given(compatibilityScoreCalculator.calculate(any(), anyString(), any(), anyString()))
                .willReturn(78);
        given(jobRoleAnalyzer.analyze(any(FiveElements.class), any(JobCategoryEnum.class)))
                .willReturn(85);
        given(companyCompatibilityJdbcRepository.insertOrIgnore(any())).willReturn(1);
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(savedEntity));
        org.mockito.BDDMockito.willThrow(saveFailure)
                .given(childSaveService).saveAllAndMarkCompleted(any(), any());

        // When & Then
        assertThatThrownBy(() -> service.analyzeCompatibility(request, USER_ID))
                .isSameAs(saveFailure);
        verify(dailyApiUsageService).restoreDailyUsage(USER_ID, usageDate);
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
