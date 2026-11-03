package ssafy.SSAju.career.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.career.caller.CompanyMatchingOpenAICaller;
import ssafy.SSAju.career.caller.ConsultationOpenAICaller;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.AnalysisTimeContext;
import ssafy.SSAju.career.domain.BirthInfo;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.career.mapper.AnalysisSnapshotMapper;
import ssafy.SSAju.career.mapper.ConsultationMapper;
import ssafy.SSAju.career.provider.ManseProvider;
import ssafy.SSAju.career.provider.PromptProvider;
import ssafy.SSAju.career.provider.SajuAnalysisFacade;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.strategy.CompanyCompatibilityAnalysisStrategy;
import ssafy.SSAju.career.strategy.ConsultationAnalysisStrategy;
import ssafy.SSAju.career.strategy.SajuAnalysisStrategy;
import ssafy.SSAju.career.util.AnalysisResponseBuilder;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.CompatibilityScoreCalculator;
import ssafy.SSAju.career.util.ForecastScoreCalculator;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.career.util.JobRoleAnalyzer;
import ssafy.SSAju.career.util.RoleCompatibilityCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.dto.request.CareerTimingRequest;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.dto.request.ConsultationRequest;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;
import ssafy.SSAju.service.CompanyInfoService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 만세력을 재사용해도 유형별 입력·결과 검증이 그대로 수행되는지, 그리고 요청 도중 시각이 흘러도
 * 분석 기준이 최초 문맥을 따르는지 검증한다(T029).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("통합 분석 검증·시간 문맥 테스트")
class UnifiedAnalysisValidationTest {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);
    private static final LocalTime DEFAULT_TIME = LocalTime.of(12, 0);

    @Mock private ConsultationOpenAICaller consultationCaller;
    @Mock private CompanyMatchingOpenAICaller compatibilityCaller;
    @Mock private UserProfileProvider userProfileProvider;
    @Mock private ManseProvider manseProvider;
    @Mock private CompanyInfoService companyInfoService;

    private final TenGodCalculator tenGodCalculator = new TenGodCalculator();
    private final RequestValidator requestValidator = new RequestValidator();
    private final SajuAnalysisFacade facade = new SajuAnalysisFacade(
            tenGodCalculator, new HiddenStemCalculator(), new CareerFortuneAnalyzer(tenGodCalculator));
    private final Clock clock = UnifiedAnalysisTestSupport.fixedClock();
    private final ManseData manseData = UnifiedAnalysisTestSupport.manseData();

    private final SajuAnalysisStrategy sajuStrategy =
            new SajuAnalysisStrategy(facade, requestValidator);

    private ConsultationAnalysisStrategy consultationStrategy() {
        return new ConsultationAnalysisStrategy(
                facade, consultationCaller, new ConsultationMapper(clock), requestValidator);
    }

    private CompanyCompatibilityAnalysisStrategy compatibilityStrategy() {
        return new CompanyCompatibilityAnalysisStrategy(
                userProfileProvider, manseProvider, companyInfoService,
                new CompatibilityScoreCalculator(tenGodCalculator, new CompatibilityValidator()),
                new JobRoleAnalyzer(), new RoleCompatibilityCalculator(), compatibilityCaller,
                new AnalysisResponseBuilder(new ForecastScoreCalculator()),
                new AnalysisSnapshotMapper(), requestValidator);
    }

    // ───────────────────────── 입력 검증 ─────────────────────────

    @Test
    @DisplayName("1900년 이전 출생정보는 세 유형 모두 거부한다")
    void shouldRejectBirthDateBeforeSupportedRange() {
        LocalDate tooOld = LocalDate.of(1899, 12, 31);

        assertThatThrownBy(() -> sajuStrategy.validateInput(new CareerTimingRequest(tooOld, BIRTH_TIME)))
                .isInstanceOf(InvalidSajuDataException.class);
        assertThatThrownBy(() -> consultationStrategy().validateInput(new ConsultationRequest(tooOld, BIRTH_TIME)))
                .isInstanceOf(InvalidSajuDataException.class);
        assertThatThrownBy(() -> compatibilityStrategy().validateInput(compatibilityRequest(tooOld, null)))
                .isInstanceOf(InvalidSajuDataException.class);
    }

    @Test
    @DisplayName("궁합은 출생 시각이 없어도 12:00으로 정규화해 통과한다")
    void shouldNormalizeMissingBirthTimeForCompatibility() {
        // Given
        CompatibilityRequest request = compatibilityRequest(BIRTH_DATE, null);

        // When
        BirthInfo birthInfo = compatibilityStrategy().birthInfo(request);

        // Then
        compatibilityStrategy().validateInput(request);
        assertThat(birthInfo.birthTime()).isEqualTo(DEFAULT_TIME);
    }

    @Test
    @DisplayName("관운·컨설팅은 출생 시각이 없으면 거부한다")
    void shouldRequireBirthTimeForSajuAndConsultation() {
        assertThatThrownBy(() -> sajuStrategy.validateInput(new CareerTimingRequest(BIRTH_DATE, null)))
                .isInstanceOf(InvalidSajuDataException.class);
        assertThatThrownBy(() -> consultationStrategy().validateInput(new ConsultationRequest(BIRTH_DATE, null)))
                .isInstanceOf(InvalidSajuDataException.class);
    }

    @Test
    @DisplayName("회사명이나 직무가 없는 궁합 요청은 거부한다")
    void shouldRejectCompatibilityWithoutCompanyOrRole() {
        CompanyCompatibilityAnalysisStrategy strategy = compatibilityStrategy();

        assertThatThrownBy(() -> strategy.validateInput(new CompatibilityRequest(
                BIRTH_DATE, BIRTH_TIME,
                new CompatibilityRequest.TargetRoleRequest(JobCategoryEnum.TECH_BACKEND, "백엔드"),
                "  ", null, null)))
                .isInstanceOf(InvalidSajuDataException.class);
        assertThatThrownBy(() -> strategy.validateInput(new CompatibilityRequest(
                BIRTH_DATE, BIRTH_TIME, null, "테스트기업", null, null)))
                .isInstanceOf(InvalidSajuDataException.class);
    }

    // ───────────────────────── 결과 검증 ─────────────────────────

    @Test
    @DisplayName("만세력을 재사용해도 결과 검증은 매번 수행한다")
    void shouldValidateResultEvenWhenManseIsReused() {
        // Given: 저장된 만세력으로 두 번 분석
        AnalysisTimeContext time = AnalysisTimeContext.from(clock);
        AnalysisPayload.SajuResultData first = sajuStrategy.analyze(
                new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME), manseData, time).result();

        // When / Then: 정상 결과는 통과하고, 손상된 결과는 거부한다
        sajuStrategy.validateResult(first);
        assertThatThrownBy(() -> sajuStrategy.validateResult(
                AnalysisPayload.SajuResultData.of(first.favoredPeriod(), -1, first.reasoning())))
                .isInstanceOf(InvalidSajuDataException.class);
    }

    @Test
    @DisplayName("만세력 데이터가 없으면 분석하지 않는다")
    void shouldRejectMissingManseData() {
        AnalysisTimeContext time = AnalysisTimeContext.from(clock);
        assertThatThrownBy(() -> sajuStrategy.analyze(
                new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME), null, time))
                .isInstanceOf(InvalidSajuDataException.class);
    }

    // ───────────────────────── 시간 문맥 고정 ─────────────────────────

    @Test
    @DisplayName("재시도가 해를 넘겨도 프롬프트는 요청 시작 시 확정된 연도를 쓴다")
    void shouldKeepPromptYearAcrossRetryOverYearBoundary() {
        // Given: 요청 시작은 2026-12-31 23:50 KST
        PromptProvider promptProvider = new PromptProvider();
        AnalysisTimeContext time = AnalysisTimeContext.from(
                Clock.fixed(Instant.parse("2026-12-31T14:50:00Z"), SERVICE_ZONE));

        // When: 재시도가 자정을 넘긴 시점에 실행돼도 같은 문맥을 넘긴다
        String prompt = promptProvider.getCareerConsultationPrompt(
                manseData.toFastApiResponse(BIRTH_DATE.toString(), BIRTH_TIME.toString()),
                new TenGodDistribution(manseData.tenGodHiddenStemAnalysis().tenGods()),
                new HiddenStems(manseData.tenGodHiddenStemAnalysis().hiddenStems()),
                manseData.dayMaster(),
                time);

        // Then
        assertThat(prompt).contains("2026년 기준 12개월").doesNotContain("2027년 기준");
    }

    @Test
    @DisplayName("대상 월은 요청 시작 월부터 5개월로 고정된다")
    void shouldFixTargetMonthsFromRequestStart() {
        // Given
        AnalysisTimeContext time = AnalysisTimeContext.from(
                Clock.fixed(Instant.parse("2026-11-02T03:00:00Z"), SERVICE_ZONE));

        // When
        List<Integer> targetMonths = time.targetMonths();

        // Then
        assertThat(targetMonths).containsExactly(11, 12, 1, 2, 3);
    }

    private CompatibilityRequest compatibilityRequest(LocalDate birthDate, LocalTime birthTime) {
        return new CompatibilityRequest(
                birthDate, birthTime,
                new CompatibilityRequest.TargetRoleRequest(JobCategoryEnum.TECH_BACKEND, "백엔드 개발자"),
                "테스트기업", LocalDate.of(2010, 5, 1), null);
    }
}
