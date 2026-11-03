package ssafy.SSAju.career.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.caller.CompanyMatchingOpenAICaller;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.AnalysisTimeContext;
import ssafy.SSAju.career.domain.BirthInfo;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.domain.CompatibilityNarrativeRequest;
import ssafy.SSAju.career.domain.FiveElements;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.CompanyFoundingDateSource;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.mapper.AnalysisSnapshotMapper;
import ssafy.SSAju.career.provider.ManseProvider;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.util.AnalysisConstants;
import ssafy.SSAju.career.util.AnalysisResponseBuilder;
import ssafy.SSAju.career.util.CompatibilityScoreCalculator;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.career.util.JobRoleAnalyzer;
import ssafy.SSAju.career.util.RoleCompatibilityCalculator;
import ssafy.SSAju.career.validator.RequestValidator;
import ssafy.SSAju.dto.external.CompatibilityNarrativeResponse;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.exception.PublicDataApiException;
import ssafy.SSAju.service.CompanyInfoService;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 기업/직무 궁합 전략.
 *
 * <p>사용자 만세력은 공통 실행 서비스가 넘겨주고, 회사 설립일시의 만세력은 이 전략이 확보한다 —
 * 회사 설립일은 요청값이 없으면 공공데이터 API로 조회해야 비로소 정해지기 때문이다. 회사 기초도
 * 같은 프로필·만세력 구조로 재사용하므로 같은 설립일시의 회사는 FastAPI를 다시 호출하지 않는다.
 *
 * <p>요청값과 실제 계산에 사용한 값(출생 시각 기본값, 설립일 보정, 취득 경로)을 입력 스냅샷에
 * 모두 남겨, 조회 시 회사를 다시 조회하지 않고도 어떤 근거로 분석했는지 알 수 있게 한다.
 *
 * <p>점수는 기존 결정론적 계산을, 문구는 기존 AI 응답과 검증을 그대로 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyCompatibilityAnalysisStrategy
        implements AnalysisStrategy<CompatibilityRequest, AnalysisPayload.CompatibilityResultData> {

    /** 출생·설립 시각이 없을 때 사용하는 기본 시각. */
    private static final LocalTime DEFAULT_TIME = LocalTime.of(12, 0);

    /** 사주 라이브러리가 지원하는 가장 이른 날짜. 이보다 이른 설립일은 이 값으로 보정한다. */
    private static final LocalDate MIN_SUPPORTED_DATE = LocalDate.of(1900, 1, 1);

    private final UserProfileProvider userProfileProvider;
    private final ManseProvider manseProvider;
    private final CompanyInfoService companyInfoService;
    private final CompatibilityScoreCalculator compatibilityScoreCalculator;
    private final JobRoleAnalyzer jobRoleAnalyzer;
    private final RoleCompatibilityCalculator roleCompatibilityCalculator;
    private final CompanyMatchingOpenAICaller companyMatchingOpenAICaller;
    private final AnalysisResponseBuilder responseBuilder;
    private final AnalysisSnapshotMapper snapshotMapper;
    private final RequestValidator requestValidator;

    @Override
    public boolean supports(AnalysisType type) {
        return AnalysisType.COMPANY_COMPATIBILITY == type;
    }

    @Override
    public Class<CompatibilityRequest> inputType() {
        return CompatibilityRequest.class;
    }

    /** 출생 시각이 없으면 기존 정책대로 12:00으로 정규화한다. */
    @Override
    public BirthInfo birthInfo(CompatibilityRequest input) {
        return new BirthInfo(input.userBirthDate(), resolveTime(input.userBirthTime()));
    }

    @Override
    public void validateInput(CompatibilityRequest input) {
        requestValidator.validateBirthInfo(input.userBirthDate(), resolveTime(input.userBirthTime()));
        if (input.companyName() == null || input.companyName().isBlank()
                || input.targetRole() == null || input.targetRole().category() == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.VALIDATION_FAILED.getMessage());
        }
    }

    @Override
    public AnalysisOutcome<AnalysisPayload.CompatibilityResultData> analyze(CompatibilityRequest input,
                                                                            ManseData manseData,
                                                                            AnalysisTimeContext time) {
        CompanyFounding founding = resolveCompanyFounding(input);
        ManseData companyManseData = companyManse(founding).getData();

        HiddenStems userHiddenStems = manseData.hiddenStems();
        String userDayMaster = manseData.dayMaster();
        HiddenStems companyHiddenStems = companyManseData.hiddenStems();
        String companyDayMaster = companyManseData.dayMaster();

        FiveElements userFiveElements = new FiveElements(manseData.fiveElements());
        FiveElements companyFiveElements = new FiveElements(companyManseData.fiveElements());

        JobCategoryEnum category = input.targetRole().category();
        // 점수 계산기가 사용자·기업 지장간/일간을 먼저 검증한다(CompatibilityValidator).
        int compatibilityScore = compatibilityScoreCalculator.calculate(
                userHiddenStems, userDayMaster, companyHiddenStems, companyDayMaster);
        int matchScore = jobRoleAnalyzer.analyze(userFiveElements, category);
        int secondaryScore = roleCompatibilityCalculator.calculateSecondary(matchScore);

        CompatibilityNarrativeResponse narrative = companyMatchingOpenAICaller.call(
                new CompatibilityNarrativeRequest(
                        new CompatibilityNarrativeRequest.SajuInfo(
                                userFiveElements, userHiddenStems, userDayMaster),
                        new CompatibilityNarrativeRequest.SajuInfo(
                                companyFiveElements, companyHiddenStems, companyDayMaster),
                        new CompatibilityNarrativeRequest.ScoreSet(
                                compatibilityScore, matchScore, matchScore, secondaryScore),
                        category, input.targetRole().detailName()),
                time);

        CompatibilityAnalysisData analysisData = new CompatibilityAnalysisData(
                new CompatibilityAnalysisData.RoleAnalysis(
                        matchScore, narrative.roleSynergy(), narrative.roleWarning()),
                responseBuilder.buildFiveElementsData(
                        userFiveElements, companyFiveElements, narrative.fiveElementsSynergyDescription()),
                responseBuilder.buildAnalysisBreakdown(compatibilityScore),
                responseBuilder.buildActionableStrategy(category, narrative.weaknessDefense(), time),
                responseBuilder.buildInterviewQuestions(narrative.interviewQuestions()),
                responseBuilder.buildRoleCompatibilities(
                        category, matchScore, secondaryScore,
                        narrative.primaryRoleReason(), narrative.secondaryRoleReason()),
                responseBuilder.buildMonthlyForecasts(userFiveElements, narrative.monthlyAdvices(), time),
                narrative.cautions());

        return new AnalysisOutcome<>(
                buildInput(input, founding, time),
                snapshotMapper.toCompatibilityResult(
                        input.companyName(),
                        new CompatibilityResponse.TargetRoleInfo(category, input.targetRole().detailName()),
                        compatibilityScore,
                        narrative.summary(),
                        analysisData));
    }

    @Override
    public void validateResult(AnalysisPayload.CompatibilityResultData result) {
        if (result.requestContext() == null || result.compatibilityScore() == null
                || result.summary() == null || result.summary().isBlank()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_RESULT_INVALID.getMessage());
        }
        if (result.compatibilityScore() < AnalysisConstants.MIN_SCORE
                || result.compatibilityScore() > AnalysisConstants.MAX_SCORE) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_RESULT_INVALID.getMessage());
        }
        if (result.monthlyForecast() == null
                || result.monthlyForecast().size() != AnalysisConstants.FORECAST_MONTH_COUNT) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_RESULT_INVALID.getMessage());
        }
        // 조회는 이 스냅샷만 읽고 회사·계산기를 다시 부르지 않으므로 중첩 항목이 비어 있으면 안 된다.
        if (result.targetRoleAnalysis() == null || result.fiveElements() == null
                || result.analysisBreakdown() == null || result.actionableStrategy() == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_RESULT_INVALID.getMessage());
        }
        if (result.expectedInterviewQuestions() == null || result.expectedInterviewQuestions().isEmpty()
                || result.roleCompatibility() == null || result.roleCompatibility().isEmpty()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_RESULT_INVALID.getMessage());
        }
    }

    private Manse companyManse(CompanyFounding founding) {
        UserProfile companyProfile = userProfileProvider.findOrCreate(founding.date(), founding.time());
        return manseProvider.findOrCreate(companyProfile);
    }

    /**
     * 실제 계산에 사용할 기업 설립일시를 확정한다.
     * <ol>
     *   <li>요청에 설립일이 있으면 그대로 사용한다.</li>
     *   <li>없으면 공공데이터 API로 조회하고, 조회도 실패하면 직접 입력을 요구한다.</li>
     *   <li>1900-01-01 이전은 사주 라이브러리 지원 범위 밖이라 1900-01-01로 보정한다.</li>
     * </ol>
     */
    private CompanyFounding resolveCompanyFounding(CompatibilityRequest request) {
        LocalDate date;
        CompanyFoundingDateSource source;
        if (request.companyFoundingDate() != null) {
            date = request.companyFoundingDate();
            source = CompanyFoundingDateSource.REQUEST;
        } else {
            log.info("기업 설립일 미제공 → 공공데이터 API 자동 조회");
            date = companyInfoService.lookupCompanyFoundingDate(request.companyName())
                    .orElseThrow(() -> new PublicDataApiException(
                            "기업 설립일을 공공데이터에서 조회할 수 없습니다. companyFoundingDate를 직접 입력해주세요."));
            source = CompanyFoundingDateSource.PUBLIC_DATA_API;
        }

        if (date.isBefore(MIN_SUPPORTED_DATE)) {
            log.warn("기업 설립일이 사주 라이브러리 지원 범위(1900-01-01) 이전입니다. 1900-01-01로 보정합니다.");
            date = MIN_SUPPORTED_DATE;
        }
        return new CompanyFounding(date, resolveTime(request.companyFoundingTime()), source);
    }

    private AnalysisPayload.CompatibilityInput buildInput(CompatibilityRequest request,
                                                           CompanyFounding founding,
                                                           AnalysisTimeContext time) {
        return new AnalysisPayload.CompatibilityInput(
                AnalysisPayload.SCHEMA_VERSION,
                request.userBirthDate(),
                request.userBirthTime(),
                resolveTime(request.userBirthTime()),
                request.companyName(),
                request.targetRole().category(),
                request.targetRole().detailName(),
                request.companyFoundingDate(),
                request.companyFoundingTime(),
                founding.date(),
                founding.time(),
                founding.source(),
                time.yearMonth(),
                time.year());
    }

    private LocalTime resolveTime(LocalTime requested) {
        return requested != null ? requested : DEFAULT_TIME;
    }

    /** 실제 계산에 사용한 기업 설립일시와 그 취득 경로. */
    private record CompanyFounding(LocalDate date, LocalTime time, CompanyFoundingDateSource source) {
    }
}
