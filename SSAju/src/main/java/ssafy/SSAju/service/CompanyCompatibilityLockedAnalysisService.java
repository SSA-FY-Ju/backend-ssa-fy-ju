package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.SSAju.annotation.DistributedLock;
import ssafy.SSAju.career.caller.CompanyMatchingOpenAICaller;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.domain.CompatibilityNarrativeRequest;
import ssafy.SSAju.career.domain.FiveElements;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.SajuPillarIndex;
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
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.exception.PublicDataApiException;
import ssafy.SSAju.repository.CompanyCompatibilityJdbcRepository;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * 기업 궁합 분석의 "캐시 미스 이후" 구간(더블체크 캐시 확인 → 쿼터 차감 → 사주 계산/AI 호출/저장)을
 * userProfile+company+role 단위 분산락으로 보호합니다(US5, T035).
 *
 * <p>{@link CompanyMatchingService}가 락 없이 빠르게 처리하는 1차 캐시 조회에서 미스가 난 요청만
 * 이 클래스로 넘어옵니다. {@code @DistributedLock}은 같은 클래스 내 self-invocation에서는 Spring AOP
 * 프록시가 작동하지 않으므로, {@code ConsultationSaveService}/{@code ConsultationInsertService}와
 * 동일한 이유로 별도 빈으로 분리되어 있습니다.
 *
 * <p><strong>락 안에서 캐시를 다시 확인하는 이유(더블체크락)</strong>: 락 대기 중이던 다른 요청이
 * 먼저 분석을 완료했을 수 있으므로, 락을 획득한 뒤에도 캐시를 재확인해야 동시 요청 간 쿼터
 * 이중 차감을 막을 수 있다. 재확인에서도 미스일 때만 쿼터를 차감한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyCompatibilityLockedAnalysisService {

    private static final LocalTime DEFAULT_BIRTH_TIME = LocalTime.of(12, 0);
    private static final LocalDate MIN_SUPPORTED_DATE = LocalDate.of(1900, 1, 1);

    private final SajuDataService sajuDataService;
    private final CompanyInfoService companyInfoService;
    private final SajuValidator sajuValidator;
    private final HiddenStemCalculator hiddenStemCalculator;
    private final CompatibilityScoreCalculator compatibilityScoreCalculator;
    private final JobRoleAnalyzer jobRoleAnalyzer;
    private final RoleCompatibilityCalculator roleCompatibilityCalculator;
    private final AnalysisResponseBuilder responseBuilder;
    private final CompanyMatchingOpenAICaller companyMatchingOpenAICaller;
    private final CompanyCompatibilityRepository companyCompatibilityRepository;
    private final CompanyCompatibilityJdbcRepository companyCompatibilityJdbcRepository;
    private final CompatibilityChildSaveService childSaveService;
    private final CompatibilityChildReadService childReadService;
    private final DailyApiUsageService dailyApiUsageService;

    @DistributedLock(key = "'lock:company-compatibility:' + #userProfile.id + ':' + #request.companyName() "
            + "+ ':' + #request.targetRole().category()")
    public CompatibilityResponse analyzeWithLock(CompatibilityRequest request, Long userId, User user,
                                                   UserProfile userProfile, Integer compatibilityMonth,
                                                   LocalTime userBirthTime) {
        Optional<CompanyCompatibility> cachedOpt = findCompletedCache(
                userId, userProfile, request, compatibilityMonth);
        if (cachedOpt.isPresent()) {
            log.info("락 안에서 캐시 재확인 히트 (compatibilityId={})", cachedOpt.get().getId());
            return childReadService.buildFromExisting(cachedOpt.get(), request);
        }

        LocalDate usageDate = dailyApiUsageService.checkAndIncrementDailyUsage(userId);
        try {
            return analyzeAndPersist(request, userId, user, userProfile, compatibilityMonth, userBirthTime);
        } catch (RuntimeException e) {
            // 보상(쿼터 복원) 자체가 실패해도 원본 예외가 유실되면 안 되므로 별도로 잡아 로그만 남기고
            // 원인으로 덧붙인 뒤, 항상 원본 예외를 그대로 던진다.
            try {
                dailyApiUsageService.restoreDailyUsage(userId, usageDate);
            } catch (RuntimeException restoreException) {
                log.error("쿼터 복원 실패 (userId={}, usageDate={})", userId, usageDate, restoreException);
                e.addSuppressed(restoreException);
            }
            throw e;
        }
    }

    private Optional<CompanyCompatibility> findCompletedCache(Long userId, UserProfile userProfile,
                                                                 CompatibilityRequest request,
                                                                 Integer compatibilityMonth) {
        return companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        userId, userProfile.getId(),
                        request.companyName(), request.targetRole().category(), compatibilityMonth)
                .filter(CompanyCompatibility::isCompleted);
    }

    /**
     * 사주 계산부터 AI 해설 생성, DB 저장까지의 신규 분석 흐름.
     *
     * <p>userProfile+company+role 단위 분산락이 동시 생성을 막으므로, 저장은 단순히
     * insert 후 자식 엔티티를 저장하는 흐름만 남는다 — insertOrIgnore 기반의
     * inserted==0/1 분기는 더 이상 필요하지 않다.
     */
    private CompatibilityResponse analyzeAndPersist(CompatibilityRequest request, Long userId,
                                                       User user, UserProfile userProfile,
                                                       Integer compatibilityMonth, LocalTime userBirthTime) {
        SajuCalculationResult sajuCalc = calculateSajuData(request, userBirthTime);
        HiddenStems userHiddenStems = sajuCalc.userHiddenStems();
        FiveElements userFiveElements = sajuCalc.userFiveElements();
        HiddenStems companyHiddenStems = sajuCalc.companyHiddenStems();
        FiveElements companyFiveElements = sajuCalc.companyFiveElements();

        // ─── 분석 계산 (점수는 규칙 기반, 해설은 AI 생성) ──────────────
        JobCategoryEnum category = request.targetRole().category();
        int compatibilityScore = compatibilityScoreCalculator.calculate(
                userHiddenStems, sajuCalc.userDayMaster(), companyHiddenStems, sajuCalc.companyDayMaster());
        int matchScore = jobRoleAnalyzer.analyze(userFiveElements, category);
        int primaryScore = matchScore;
        int secondaryScore = roleCompatibilityCalculator.calculateSecondary(primaryScore);

        CompatibilityNarrativeRequest narrativeRequest = new CompatibilityNarrativeRequest(
                new CompatibilityNarrativeRequest.SajuInfo(
                        userFiveElements, userHiddenStems, sajuCalc.userDayMaster()),
                new CompatibilityNarrativeRequest.SajuInfo(
                        companyFiveElements, companyHiddenStems, sajuCalc.companyDayMaster()),
                new CompatibilityNarrativeRequest.ScoreSet(
                        compatibilityScore, matchScore, primaryScore, secondaryScore),
                category, request.targetRole().detailName());
        CompatibilityNarrativeResponse narrative = companyMatchingOpenAICaller.call(narrativeRequest);

        CompatibilityAnalysisData analysisData = new CompatibilityAnalysisData(
                new CompatibilityAnalysisData.RoleAnalysis(matchScore, narrative.roleSynergy(), narrative.roleWarning()),
                responseBuilder.buildFiveElementsData(
                        userFiveElements, companyFiveElements, narrative.fiveElementsSynergyDescription()),
                responseBuilder.buildAnalysisBreakdown(compatibilityScore),
                responseBuilder.buildActionableStrategy(category, narrative.weaknessDefense()),
                responseBuilder.buildInterviewQuestions(narrative.interviewQuestions()),
                responseBuilder.buildRoleCompatibilities(
                        category, primaryScore, secondaryScore,
                        narrative.primaryRoleReason(), narrative.secondaryRoleReason()),
                responseBuilder.buildMonthlyForecasts(userFiveElements, narrative.monthlyAdvices()),
                narrative.cautions()
        );
        String summary = narrative.summary();

        CompanyCompatibility root = CompanyCompatibility.builder()
                .userProfile(userProfile)
                .user(user)
                .companyName(request.companyName())
                .targetRoleCategory(request.targetRole().category())
                .targetRoleDetailName(request.targetRole().detailName())
                .compatibilityScore(compatibilityScore)
                .summary(summary)
                .compatibilityMonth(compatibilityMonth)
                .build();

        companyCompatibilityJdbcRepository.insert(root);
        CompanyCompatibility saved = companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        userId, userProfile.getId(),
                        request.companyName(), request.targetRole().category(), compatibilityMonth)
                .orElseThrow(() -> new DataAccessException("CompanyCompatibility 조회 실패"));

        // 자식 엔티티 전체 저장을 단일 트랜잭션(REQUIRES_NEW)으로 위임
        childSaveService.saveAllAndMarkCompleted(saved, analysisData);

        log.info("기업 궁합 분석 완료: compatibilityScore={}", compatibilityScore);
        return buildNewResponse(saved, request, analysisData);
    }

    /** 사용자·기업 사주(FastAPI/공공데이터 호출 포함)를 계산한 결과. */
    private record SajuCalculationResult(
            HiddenStems userHiddenStems, String userDayMaster, FiveElements userFiveElements,
            HiddenStems companyHiddenStems, String companyDayMaster, FiveElements companyFiveElements
    ) {}

    private SajuCalculationResult calculateSajuData(CompatibilityRequest request, LocalTime userBirthTime) {
        FastAPIResponse userSaju = sajuDataService.fetchSajuFromFastAPI(
                request.userBirthDate(), userBirthTime);
        sajuValidator.validate(userSaju);

        HiddenStems userHiddenStems = hiddenStemCalculator.calculate(userSaju.earthlyBranches());
        String userDayMaster = userSaju.heavenlyStems().get(SajuPillarIndex.DAY_INDEX);
        FiveElements userFiveElements = new FiveElements(userSaju.fiveElements());

        LocalDate companyDate = resolveCompanyFoundingDate(request);
        LocalTime companyTime = request.companyFoundingTime() != null
                ? request.companyFoundingTime() : DEFAULT_BIRTH_TIME;

        FastAPIResponse companySaju = sajuDataService.fetchSajuFromFastAPI(companyDate, companyTime);
        sajuValidator.validate(companySaju);

        HiddenStems companyHiddenStems = hiddenStemCalculator.calculate(companySaju.earthlyBranches());
        String companyDayMaster = companySaju.heavenlyStems().get(SajuPillarIndex.DAY_INDEX);
        FiveElements companyFiveElements = new FiveElements(companySaju.fiveElements());

        return new SajuCalculationResult(userHiddenStems, userDayMaster, userFiveElements,
                companyHiddenStems, companyDayMaster, companyFiveElements);
    }

    /**
     * 기업 설립일자를 결정합니다.
     * <ol>
     *   <li>요청에 {@code companyFoundingDate}가 있으면 그대로 사용</li>
     *   <li>없으면 공공데이터 API로 {@code companyName}(corpNm) 조회</li>
     *   <li>API 조회도 실패하면 {@link PublicDataApiException} 발생</li>
     *   <li>1900-01-01 이전 날짜는 사주 라이브러리 지원 범위 밖이므로 1900-01-01로 자동 조정</li>
     * </ol>
     */
    private LocalDate resolveCompanyFoundingDate(CompatibilityRequest request) {
        LocalDate date;
        if (request.companyFoundingDate() != null) {
            date = request.companyFoundingDate();
        } else {
            log.info("기업 설립일 미제공 → 공공데이터 API 자동 조회: company={}", request.companyName());
            date = companyInfoService.lookupCompanyFoundingDate(request.companyName())
                    .orElseThrow(() -> new PublicDataApiException(
                            "기업 설립일을 공공데이터에서 조회할 수 없습니다. companyFoundingDate를 직접 입력해주세요."));
        }

        if (date.isBefore(MIN_SUPPORTED_DATE)) {
            log.warn("기업 설립일({})이 사주 라이브러리 지원 범위(1900-01-01) 이전입니다. 1900-01-01로 자동 조정합니다.",
                    date);
            return MIN_SUPPORTED_DATE;
        }
        return date;
    }

    private CompatibilityResponse buildNewResponse(CompanyCompatibility saved,
                                                      CompatibilityRequest request,
                                                      CompatibilityAnalysisData data) {
        CompatibilityAnalysisData.StrategyInfo s = data.strategy();
        return new CompatibilityResponse(
                saved.getId(),
                buildRequestContext(saved, request),
                saved.getCompatibilityScore(),
                saved.getSummary(),
                new CompatibilityResponse.TargetRoleAnalysis(
                        data.roleAnalysis().matchScore(),
                        data.roleAnalysis().synergy(),
                        data.roleAnalysis().warning()),
                new CompatibilityResponse.FiveElements(
                        data.fiveElements().userDistribution(),
                        data.fiveElements().companyDistribution(),
                        data.fiveElements().synergyDescription()),
                new CompatibilityResponse.AnalysisBreakdown(
                        data.breakdown().characterMatch(),
                        data.breakdown().potentialSynergy(),
                        data.breakdown().longTermStability()),
                new CompatibilityResponse.ActionableStrategy(
                        s.keywords(), s.weaknessDefense(),
                        new CompatibilityResponse.ActionableStrategy.BestTiming(
                                s.luckyDays(), s.preferredTime())),
                data.questions().stream().map(q -> new CompatibilityResponse.InterviewQuestion(
                        q.question(), q.intent())).toList(),
                data.roles().stream().map(r -> new CompatibilityResponse.RoleCompatibility(
                        r.roleName(), r.score(), r.reason(), r.tag())).toList(),
                data.forecasts().stream().map(f -> new CompatibilityResponse.MonthlyForecast(
                        f.month(), f.score(), f.status(), f.advice())).toList(),
                data.cautions()
        );
    }

    private CompatibilityResponse.RequestContext buildRequestContext(CompanyCompatibility saved,
                                                                       CompatibilityRequest request) {
        return new CompatibilityResponse.RequestContext(
                saved.getCompanyName(),
                new CompatibilityResponse.TargetRoleInfo(
                        saved.getTargetRoleCategory(),
                        request.targetRole().detailName()
                )
        );
    }
}
