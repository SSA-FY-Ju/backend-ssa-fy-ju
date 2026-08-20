package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.SSAju.career.caller.CompanyMatchingOpenAICaller;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.domain.CompatibilityNarrativeRequest;
import ssafy.SSAju.career.domain.FiveElements;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.SajuPillarIndex;
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
import ssafy.SSAju.exception.PublicDataApiException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Optional;

/**
 * 기업/직무 궁합 분석 오케스트레이션 서비스.
 *
 * <p><strong>락 배치 원칙</strong>: 분산락은 "실제로 동시에 겹치면 안 되는 최소 구간"(DB 저장)에만
 * 건다. FastAPI/공공데이터/OpenAI 같은 외부 I/O는 걸리는 시간이 들쭉날쭉하고 통제할 수 없으므로
 * 락 밖에 둔다 — 락 안에 넣으면 락 임대시간(leaseTime)보다 외부 호출이 오래 걸릴 때 락이 중간에
 * 만료되어 동시성 보장이 깨진다. 이 원칙은 {@link ssafy.SSAju.career.provider.SajuResultProvider}/
 * {@link UserProfileProvider}/{@link ConsultationSaveService}와 동일하다.
 *
 * <p>동일 (프로필, 회사, 직무, 월) 조합에 대한 동시 요청이 FastAPI/OpenAI를 중복 호출하는 것은
 * 허용한다(트레이드오프로 감수) — 최종 저장은 {@link CompanyCompatibilitySaveService}의 락이
 * 정확히 1건만 남도록 보장한다.
 *
 * <p>{@code @Transactional} 없음: FastAPI/OpenAI 외부 I/O 동안 DB 커넥션을 점유하지 않도록
 * 트랜잭션을 분리. DB 저장은 {@link CompanyCompatibilitySaveService}에 위임되며, 그 클래스에도
 * {@code @Transactional}이 없다 — 락이 메서드를 감싸고 그 안의 저장소 호출은 Spring Data JPA
 * 기본 트랜잭션으로 개별 처리된다(중첩 트랜잭션 없음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyMatchingService {

    private static final LocalTime DEFAULT_BIRTH_TIME = LocalTime.of(12, 0);
    private static final LocalDate MIN_SUPPORTED_DATE = LocalDate.of(1900, 1, 1);

    private final SajuDataService sajuDataService;
    private final CompanyInfoService companyInfoService;
    private final UserProfileProvider userProfileProvider;
    private final SajuValidator sajuValidator;
    private final HiddenStemCalculator hiddenStemCalculator;
    private final CompatibilityScoreCalculator compatibilityScoreCalculator;
    private final JobRoleAnalyzer jobRoleAnalyzer;
    private final RoleCompatibilityCalculator roleCompatibilityCalculator;
    private final AnalysisResponseBuilder responseBuilder;
    private final CompanyMatchingOpenAICaller companyMatchingOpenAICaller;
    private final CompanyCompatibilityRepository companyCompatibilityRepository;
    private final CompatibilityChildReadService childReadService;
    private final CompanyCompatibilitySaveService compatibilitySaveService;
    private final UserRepository userRepository;
    /** KST 기준 현재 월 계산용 Clock. 테스트에서 고정 시각 주입 가능. */
    private final Clock clock;
    private final DailyApiUsageService dailyApiUsageService;

    public CompatibilityResponse analyzeCompatibility(CompatibilityRequest request, Long userId) {
        log.info("기업 궁합 분석 시작");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        LocalTime userBirthTime = resolveUserBirthTime(request);
        UserProfile userProfile = userProfileProvider.findOrCreate(
                request.userBirthDate(), userBirthTime);

        YearMonth now = YearMonth.now(clock);
        Integer compatibilityMonth = now.getYear() * 100 + now.getMonthValue();

        Optional<CompanyCompatibility> cachedOpt = findCompletedCache(userId, userProfile, request, compatibilityMonth);
        if (cachedOpt.isPresent()) {
            log.info("이번 달 궁합 분석 캐시 히트 (compatibilityId={})", cachedOpt.get().getId());
            return childReadService.buildFromExisting(cachedOpt.get(), request);
        }

        // 캐시 미스: 사주 계산·AI 호출과 DB 저장을 감싸 실패 시 쿼터를 복원한다.
        LocalDate usageDate = dailyApiUsageService.checkAndIncrementDailyUsage(userId);
        try {
            return analyzeAndPersist(request, user, userProfile, compatibilityMonth, userBirthTime);
        } catch (RuntimeException e) {
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
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonthAndCompletedTrue(
                        userId, userProfile.getId(),
                        request.companyName(), request.targetRole().category(), compatibilityMonth);
    }

    /**
     * 사주 계산부터 AI 해설 생성까지의 신규 분석 흐름(외부 I/O, 락 없음). 마지막 저장 단계만
     * {@link CompanyCompatibilitySaveService}에 위임해 (사용자, 프로필, 회사, 직무, 월) 단위
     * 분산락으로 보호한다.
     */
    private CompatibilityResponse analyzeAndPersist(CompatibilityRequest request,
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
        int secondaryScore = roleCompatibilityCalculator.calculateSecondary(matchScore);

        CompatibilityNarrativeRequest narrativeRequest = new CompatibilityNarrativeRequest(
                new CompatibilityNarrativeRequest.SajuInfo(
                        userFiveElements, userHiddenStems, sajuCalc.userDayMaster()),
                new CompatibilityNarrativeRequest.SajuInfo(
                        companyFiveElements, companyHiddenStems, sajuCalc.companyDayMaster()),
                new CompatibilityNarrativeRequest.ScoreSet(
                        compatibilityScore, matchScore, matchScore, secondaryScore),
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
                        category, matchScore, secondaryScore,
                        narrative.primaryRoleReason(), narrative.secondaryRoleReason()),
                responseBuilder.buildMonthlyForecasts(userFiveElements, narrative.monthlyAdvices()),
                narrative.cautions()
        );
        String summary = narrative.summary();

        CompanyCompatibility root = CompanyCompatibility.builder()
                .userProfile(userProfile)
                .user(user)
                .companyName(request.companyName())
                .targetRoleCategory(category)
                .targetRoleDetailName(request.targetRole().detailName())
                .compatibilityScore(compatibilityScore)
                .summary(summary)
                .compatibilityMonth(compatibilityMonth)
                .build();

        // 락 안에서 "이미 완료된 행이 있으면 재사용, 없으면 저장"까지 처리 — 동시에 완전히
        // 동일한 요청이 왔다면 이 시점에 승자의 결과로 수렴한다(우리가 방금 계산한 값은 버려짐).
        CompanyCompatibility saved = compatibilitySaveService.saveWithLock(root, analysisData);

        log.info("기업 궁합 분석 완료: compatibilityId={}", saved.getId());
        return childReadService.buildFromExisting(saved, request);
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

    private LocalTime resolveUserBirthTime(CompatibilityRequest request) {
        return request.userBirthTime() != null ? request.userBirthTime() : DEFAULT_BIRTH_TIME;
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
}
