package ssafy.SSAju.career.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.career.caller.CompanyMatchingOpenAICaller;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.AnalysisTimeContext;
import ssafy.SSAju.career.domain.BirthInfo;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.CompanyFoundingDateSource;
import ssafy.SSAju.career.mapper.AnalysisSnapshotMapper;
import ssafy.SSAju.career.provider.ManseProvider;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.util.AnalysisResponseBuilder;
import ssafy.SSAju.career.util.CompatibilityScoreCalculator;
import ssafy.SSAju.career.util.ForecastMonthCalculator;
import ssafy.SSAju.career.util.ForecastScoreCalculator;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.career.util.JobRoleAnalyzer;
import ssafy.SSAju.career.util.RoleCompatibilityCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.career.validator.CompatibilityValidator;
import ssafy.SSAju.career.validator.RequestValidator;
import ssafy.SSAju.dto.external.CompatibilityNarrativeResponse;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.exception.PublicDataApiException;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyCompatibilityAnalysisStrategy 단위 테스트")
class CompanyCompatibilityAnalysisStrategyTest {

    private static final LocalDate USER_BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime DEFAULT_TIME = LocalTime.of(12, 0);
    private static final String COMPANY_NAME = "테스트기업";

    @Mock private UserProfileProvider userProfileProvider;
    @Mock private ManseProvider manseProvider;
    @Mock private ssafy.SSAju.service.CompanyInfoService companyInfoService;
    @Mock private CompanyMatchingOpenAICaller openAICaller;

    private final Clock clock = UnifiedAnalysisTestSupport.fixedClock();
    private final ForecastMonthCalculator forecastMonthCalculator = new ForecastMonthCalculator(clock);
    private final AnalysisTimeContext timeContext = AnalysisTimeContext.from(clock);

    private CompanyCompatibilityAnalysisStrategy strategy;

    @BeforeEach
    void setUp() {
        AnalysisResponseBuilder responseBuilder = new AnalysisResponseBuilder(
                new ForecastScoreCalculator(), forecastMonthCalculator, clock);
        strategy = new CompanyCompatibilityAnalysisStrategy(
                userProfileProvider, manseProvider, companyInfoService,
                new CompatibilityScoreCalculator(new TenGodCalculator(), new CompatibilityValidator()),
                new JobRoleAnalyzer(), new RoleCompatibilityCalculator(), openAICaller, responseBuilder,
                new AnalysisSnapshotMapper(), new RequestValidator());
    }

    @Test
    @DisplayName("기업 궁합 유형만 지원한다")
    void shouldSupportOnlyCompatibilityType() {
        assertThat(strategy.supports(AnalysisType.COMPANY_COMPATIBILITY)).isTrue();
        assertThat(strategy.supports(AnalysisType.SAJU)).isFalse();
        assertThat(strategy.supports(AnalysisType.CAREER_CONSULTATION)).isFalse();
    }

    @Test
    @DisplayName("출생 시각이 없으면 12:00으로 정규화한다")
    void shouldNormalizeMissingBirthTime() {
        // When
        BirthInfo birthInfo = strategy.birthInfo(request(null, LocalDate.of(2010, 5, 1), null));

        // Then
        assertThat(birthInfo).isEqualTo(new BirthInfo(USER_BIRTH_DATE, DEFAULT_TIME));
    }

    @Test
    @DisplayName("요청 설립일이 있으면 공공데이터를 조회하지 않고 요청값 취득 경로를 남긴다")
    void shouldUseRequestedFoundingDate() {
        // Given
        LocalDate foundingDate = LocalDate.of(2010, 5, 1);
        givenCompanyManse(foundingDate, DEFAULT_TIME);
        givenNarrative();

        // When
        AnalysisOutcome<AnalysisPayload.CompatibilityResultData> outcome = strategy.analyze(
                request(LocalTime.of(9, 15), foundingDate, null),
                UnifiedAnalysisTestSupport.manseData(), timeContext);

        // Then
        AnalysisPayload.CompatibilityInput input = (AnalysisPayload.CompatibilityInput) outcome.input();
        assertThat(input.companyFoundingDateSource()).isEqualTo(CompanyFoundingDateSource.REQUEST);
        assertThat(input.requestedCompanyFoundingDate()).isEqualTo(foundingDate);
        assertThat(input.resolvedCompanyFoundingDate()).isEqualTo(foundingDate);
        assertThat(input.requestedCompanyFoundingTime()).isNull();
        assertThat(input.resolvedCompanyFoundingTime()).isEqualTo(DEFAULT_TIME);
        assertThat(input.requestedUserBirthTime()).isEqualTo(LocalTime.of(9, 15));
        assertThat(input.compatibilityMonth()).isEqualTo(timeContext.yearMonth());
        verify(companyInfoService, never()).lookupCompanyFoundingDate(any());
    }

    @Test
    @DisplayName("요청 설립일이 없으면 공공데이터로 조회하고 취득 경로를 남긴다")
    void shouldLookUpFoundingDateWhenMissing() {
        // Given
        LocalDate lookedUp = LocalDate.of(1999, 12, 1);
        given(companyInfoService.lookupCompanyFoundingDate(COMPANY_NAME)).willReturn(Optional.of(lookedUp));
        givenCompanyManse(lookedUp, DEFAULT_TIME);
        givenNarrative();

        // When
        AnalysisOutcome<AnalysisPayload.CompatibilityResultData> outcome = strategy.analyze(
                request(null, null, null), UnifiedAnalysisTestSupport.manseData(), timeContext);

        // Then
        AnalysisPayload.CompatibilityInput input = (AnalysisPayload.CompatibilityInput) outcome.input();
        assertThat(input.companyFoundingDateSource()).isEqualTo(CompanyFoundingDateSource.PUBLIC_DATA_API);
        assertThat(input.requestedCompanyFoundingDate()).isNull();
        assertThat(input.resolvedCompanyFoundingDate()).isEqualTo(lookedUp);
    }

    @Test
    @DisplayName("1900년 이전 설립일은 지원 범위로 보정해 저장한다")
    void shouldAdjustFoundingDateBeforeSupportedRange() {
        // Given
        LocalDate adjusted = LocalDate.of(1900, 1, 1);
        givenCompanyManse(adjusted, DEFAULT_TIME);
        givenNarrative();

        // When
        AnalysisOutcome<AnalysisPayload.CompatibilityResultData> outcome = strategy.analyze(
                request(null, LocalDate.of(1880, 7, 7), null),
                UnifiedAnalysisTestSupport.manseData(), timeContext);

        // Then
        AnalysisPayload.CompatibilityInput input = (AnalysisPayload.CompatibilityInput) outcome.input();
        assertThat(input.requestedCompanyFoundingDate()).isEqualTo(LocalDate.of(1880, 7, 7));
        assertThat(input.resolvedCompanyFoundingDate()).isEqualTo(adjusted);
    }

    @Test
    @DisplayName("설립일을 어디서도 구할 수 없으면 직접 입력을 요구한다")
    void shouldFailWhenFoundingDateUnavailable() {
        // Given
        given(companyInfoService.lookupCompanyFoundingDate(COMPANY_NAME)).willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> strategy.analyze(request(null, null, null),
                UnifiedAnalysisTestSupport.manseData(), timeContext))
                .isInstanceOf(PublicDataApiException.class);
        verify(openAICaller, never()).call(any());
    }

    @Test
    @DisplayName("완성 결과에 요청 맥락·점수·월별 운세가 모두 담긴다")
    void shouldBuildCompletedResult() {
        // Given
        LocalDate foundingDate = LocalDate.of(2010, 5, 1);
        givenCompanyManse(foundingDate, DEFAULT_TIME);
        givenNarrative();

        // When
        AnalysisPayload.CompatibilityResultData result = strategy.analyze(
                request(null, foundingDate, null),
                UnifiedAnalysisTestSupport.manseData(), timeContext).result();

        // Then
        assertThat(result.requestContext().companyName()).isEqualTo(COMPANY_NAME);
        assertThat(result.requestContext().targetRole().category()).isEqualTo(JobCategoryEnum.TECH_BACKEND);
        assertThat(result.compatibilityScore()).isBetween(0, 100);
        assertThat(result.summary()).isEqualTo("요약");
        assertThat(result.monthlyForecast()).hasSize(5);
        assertThat(result.expectedInterviewQuestions()).hasSize(1);
        assertThat(result.actionableStrategy().bestTiming().luckyDays()).hasSize(3);
        strategy.validateResult(result);
    }

    @Test
    @DisplayName("회사 기초도 같은 프로필·만세력 구조로 확보한다")
    void shouldReuseProfileAndManseForCompany() {
        // Given
        LocalDate foundingDate = LocalDate.of(2010, 5, 1);
        givenCompanyManse(foundingDate, DEFAULT_TIME);
        givenNarrative();

        // When
        strategy.analyze(request(null, foundingDate, null),
                UnifiedAnalysisTestSupport.manseData(), timeContext);

        // Then
        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalTime> timeCaptor = ArgumentCaptor.forClass(LocalTime.class);
        verify(userProfileProvider).findOrCreate(dateCaptor.capture(), timeCaptor.capture());
        assertThat(dateCaptor.getValue()).isEqualTo(foundingDate);
        assertThat(timeCaptor.getValue()).isEqualTo(DEFAULT_TIME);
        verify(manseProvider).findOrCreate(any(UserProfile.class));
    }

    private void givenCompanyManse(LocalDate foundingDate, LocalTime foundingTime) {
        UserProfile companyProfile = UnifiedAnalysisTestSupport.newProfile(foundingDate, foundingTime);
        given(userProfileProvider.findOrCreate(foundingDate, foundingTime)).willReturn(companyProfile);
        given(manseProvider.findOrCreate(companyProfile)).willReturn(Manse.builder()
                .userProfile(companyProfile)
                .data(UnifiedAnalysisTestSupport.manseData())
                .fetchedAt(clock.instant())
                .build());
    }

    private void givenNarrative() {
        given(openAICaller.call(any())).willReturn(new CompatibilityNarrativeResponse(
                "요약", "시너지", "경고", "오행 시너지", "약점 방어",
                List.of(new CompatibilityNarrativeResponse.InterviewQuestion("질문", "의도")),
                "주 직무 사유", "보조 직무 사유",
                forecastMonthCalculator.currentTargetMonths().stream()
                        .map(month -> new CompatibilityNarrativeResponse.MonthlyAdvice(month, month + "월 조언"))
                        .toList(),
                List.of("주의사항")));
    }

    private CompatibilityRequest request(LocalTime userBirthTime,
                                          LocalDate foundingDate,
                                          LocalTime foundingTime) {
        return new CompatibilityRequest(
                USER_BIRTH_DATE,
                userBirthTime,
                new CompatibilityRequest.TargetRoleRequest(JobCategoryEnum.TECH_BACKEND, "백엔드 개발자"),
                COMPANY_NAME,
                foundingDate,
                foundingTime);
    }
}
