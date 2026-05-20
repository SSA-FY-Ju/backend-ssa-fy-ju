package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.domain.FiveElements;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.entity.*;
import ssafy.SSAju.career.enums.SajuPillarIndex;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.util.*;
import ssafy.SSAju.career.validator.SajuValidator;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.PublicDataApiException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 기업/직무 궁합 분석 오케스트레이션 서비스.
 *
 * <p><strong>이 클래스의 단일 책임</strong>: 분석 흐름 제어
 * <ol>
 *   <li>사주 계산 (FastAPI 호출 → validate → 오행/십신 계산)</li>
 *   <li>INSERT IGNORE 기반 Race Condition 안전 처리</li>
 *   <li>신규/기존 분기 후 응답 반환</li>
 * </ol>
 *
 * <p>자식 엔티티 저장은 {@link CompatibilityChildSaveService}에 위임합니다.
 * DTO 포매팅은 {@link AnalysisResponseBuilder},
 * 비즈니스 계산은 각 Calculator 클래스에 위임합니다.
 *
 * <p>{@code @Transactional} 없음: FastAPI 외부 I/O 동안 DB 커넥션을 점유하지 않도록
 * 트랜잭션을 분리. 각 DB 작업은 Repository 또는 {@link CompatibilityChildSaveService}의
 * {@code @Transactional}에 의해 실행됩니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyMatchingService {

    private static final LocalTime DEFAULT_BIRTH_TIME = LocalTime.of(12, 0);
    private static final LocalDate MIN_SUPPORTED_DATE = LocalDate.of(1900, 1, 1);

    // ─────────────────────────────────────────
    // 외부 서비스 / 유틸리티
    // ─────────────────────────────────────────
    private final SajuDataService sajuDataService;
    private final CompanyInfoService companyInfoService;
    private final UserProfileProvider userProfileProvider;
    private final SajuValidator sajuValidator;
    private final TenGodCalculator tenGodCalculator;
    private final HiddenStemCalculator hiddenStemCalculator;
    private final CompatibilityScoreCalculator compatibilityScoreCalculator;
    private final JobRoleAnalyzer jobRoleAnalyzer;
    private final AnalysisResponseBuilder responseBuilder;

    // ─────────────────────────────────────────
    // 레포지토리 / 자식 저장 서비스
    // ─────────────────────────────────────────
    private final CompanyCompatibilityRepository companyCompatibilityRepository;
    private final CompanyCompatibilityJdbcRepository companyCompatibilityJdbcRepository;
    private final CompatibilityChildSaveService childSaveService;
    private final UserRepository userRepository;

    // 캐시 재사용 경로(buildResponseFromExisting)에서 자식 엔티티 조회용
    private final TargetRoleAnalysisRepository targetRoleAnalysisRepository;
    private final FiveElementsAnalysisRepository fiveElementsAnalysisRepository;
    private final AnalysisBreakdownRepository analysisBreakdownRepository;
    private final ActionableStrategyRepository actionableStrategyRepository;
    private final ActionableKeywordRepository actionableKeywordRepository;
    private final LuckyDayRepository luckyDayRepository;
    private final ExpectedInterviewQuestionRepository expectedInterviewQuestionRepository;
    private final RoleCompatibilityRepository roleCompatibilityRepository;
    private final MonthlyForecastRepository monthlyForecastRepository;
    private final CautionRepository cautionRepository;

    public CompatibilityResponse analyzeCompatibility(CompatibilityRequest request, Long userId) {
        log.info("기업 궁합 분석 시작");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        // ─── 사용자 사주 계산 ──────────────────────────────────────
        LocalTime userBirthTime = resolveUserBirthTime(request);
        UserProfile userProfile = userProfileProvider.findOrCreate(
                request.userBirthDate(), userBirthTime);

        FastAPIResponse userSaju = sajuDataService.fetchSajuFromFastAPI(
                request.userBirthDate(), userBirthTime);
        sajuValidator.validate(userSaju);

        HiddenStems userHiddenStems = hiddenStemCalculator.calculate(userSaju.earthlyBranches());
        String userDayMaster = userSaju.heavenlyStems().get(SajuPillarIndex.DAY_INDEX);
        FiveElements userFiveElements = new FiveElements(userSaju.fiveElements());

        // ─── 기업 사주 계산 ──────────────────────────────────────
        LocalDate companyDate = resolveCompanyFoundingDate(request);
        LocalTime companyTime = request.companyFoundingTime() != null
                ? request.companyFoundingTime() : DEFAULT_BIRTH_TIME;

        FastAPIResponse companySaju = sajuDataService.fetchSajuFromFastAPI(companyDate, companyTime);
        sajuValidator.validate(companySaju);

        HiddenStems companyHiddenStems = hiddenStemCalculator.calculate(companySaju.earthlyBranches());
        String companyDayMaster = companySaju.heavenlyStems().get(SajuPillarIndex.DAY_INDEX);
        FiveElements companyFiveElements = new FiveElements(companySaju.fiveElements());

        // ─── 분석 계산 ──────────────────────────────────────────
        int compatibilityScore = compatibilityScoreCalculator.calculate(
                userHiddenStems, userDayMaster, companyHiddenStems, companyDayMaster);

        CompatibilityAnalysisData analysisData = new CompatibilityAnalysisData(
                jobRoleAnalyzer.analyze(userFiveElements, request.targetRole().category()),
                responseBuilder.buildFiveElementsData(userFiveElements, companyFiveElements),
                responseBuilder.buildAnalysisBreakdown(compatibilityScore),
                responseBuilder.buildActionableStrategy(request.targetRole().category()),
                responseBuilder.buildInterviewQuestions(request.targetRole().category()),
                responseBuilder.buildRoleCompatibilities(request.targetRole().category(), userFiveElements),
                responseBuilder.buildMonthlyForecasts(userFiveElements),
                responseBuilder.buildCautions(userFiveElements, request.targetRole().category())
        );
        String summary = responseBuilder.buildSummary(compatibilityScore, request.targetRole().category());

        // ───────────────────────────────────────────────────────────────────
        // INSERT IGNORE: DB 제약(UNIQUE) 기반 동시 요청 안전 처리
        //
        // 설계 의도:
        // 1. JDBC로 root 엔티티만 먼저 삽입 (즉시 자동 커밋)
        //    - UNIQUE(user_profile_id, company_name, target_role_category) 제약 위반 시 반환값 0
        // 2. inserted 값에 따라 신규(1) vs 기존(0) 분기 처리
        // 3. 신규이면 자식 엔티티들을 각 Repository 트랜잭션으로 저장
        //
        // 레이스 컨디션 시나리오:
        // 시간순서 | 요청 A              | 요청 B
        // ────────┼──────────────────────┼──────────────────────
        // T1     | INSERT IGNORE 실행    |
        // T2     | root 커밋 (inserted=1)|
        // T3     |                       | INSERT IGNORE 실행
        // T4     |                       | UNIQUE 제약 위반, inserted=0 반환
        // T5     |                       | buildResponseFromExisting() 호출
        // T6     | saveAllChildren() 시작|
        // T7     |                       | 자식 엔티티들이 아직 저장 중 → 불완전한 응답 반환 위험
        // T8     | 자식 엔티티들 저장 완료|
        //
        // 위 시나리오는 아래 두 가지로 해결됩니다:
        // - Option A: completed 플래그 → inserted==0 + completed==false 시 캐시 차단
        // - Option B: CompatibilityChildSaveService(@Transactional REQUIRES_NEW) → 자식 원자적 저장
        // ───────────────────────────────────────────────────────────────────
        CompanyCompatibility root = CompanyCompatibility.builder()
                .userProfile(userProfile)
                .user(user)
                .companyName(request.companyName())
                .targetRoleCategory(request.targetRole().category())
                .targetRoleDetailName(request.targetRole().detailName())
                .compatibilityScore(compatibilityScore)
                .summary(summary)
                .build();

        int inserted = companyCompatibilityJdbcRepository.insertOrIgnore(root);

        CompanyCompatibility saved = companyCompatibilityRepository
                .findByUserProfile_IdAndCompanyNameAndTargetRoleCategory(
                        userProfile.getId(),
                        request.companyName(),
                        request.targetRole().category())
                .orElseThrow(() -> new ssafy.SSAju.exception.DataAccessException(
                        "CompanyCompatibility 조회 실패"));

        if (inserted == 0) {
            if (saved.isCompleted()) {
                // Option A: completed=true → 자식 데이터가 완전히 저장된 캐시만 재사용
                log.info("완료된 궁합 분석 캐시 재사용 (compatibilityId={})", saved.getId());
                return buildResponseFromExisting(saved, request);
            }
            // completed=false: 다른 요청이 자식 저장 진행 중 → 현재 계산 결과로 응답
            // (재계산 없이 이미 인메모리에 있는 결과를 그대로 반환, DB 저장은 진행 중인 요청에 위임)
            log.info("자식 저장 진행 중인 기존 레코드 감지 (compatibilityId={}), 현재 계산 결과로 응답",
                    saved.getId());
            return buildNewResponse(saved, request, analysisData);
        }

        // Option B: 자식 엔티티 전체 저장을 단일 트랜잭션(REQUIRES_NEW)으로 위임
        // 실패 시 모든 자식이 롤백되어 부분 저장 방지
        childSaveService.saveAllAndMarkCompleted(saved, analysisData);

        log.info("기업 궁합 분석 완료: compatibilityScore={}", compatibilityScore);
        return buildNewResponse(saved, request, analysisData);
    }

    // ─────────────────────────────────────────
    // private: 결정 로직
    // ─────────────────────────────────────────

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

    // ─────────────────────────────────────────
    // private: 응답 빌드
    // ─────────────────────────────────────────

    /**
     * 캐시 재사용 경로: DB에서 모든 자식 엔티티를 로드하여 응답을 구성합니다.
     * 첫 번째 요청과 두 번째 이후 요청이 동일한 응답 구조를 반환하도록 보장합니다.
     */
    private CompatibilityResponse buildResponseFromExisting(CompanyCompatibility saved,
                                                              CompatibilityRequest request) {
        CompatibilityResponse.TargetRoleAnalysis targetRoleAnalysis =
                targetRoleAnalysisRepository.findByCompanyCompatibility_Id(saved.getId())
                        .map(e -> new CompatibilityResponse.TargetRoleAnalysis(
                                e.getMatchScore(), e.getSynergy(), e.getWarning()))
                        .orElse(null);

        CompatibilityResponse.FiveElements fiveElements =
                fiveElementsAnalysisRepository.findByCompanyCompatibility_Id(saved.getId())
                        .map(e -> new CompatibilityResponse.FiveElements(
                                e.getUserDistribution(), e.getCompanyDistribution(), e.getSynergyDescription()))
                        .orElse(null);

        CompatibilityResponse.AnalysisBreakdown analysisBreakdown =
                analysisBreakdownRepository.findByCompanyCompatibility_Id(saved.getId())
                        .map(e -> new CompatibilityResponse.AnalysisBreakdown(
                                e.getCharacterMatch(), e.getPotentialSynergy(), e.getLongTermStability()))
                        .orElse(null);

        CompatibilityResponse.ActionableStrategy actionableStrategy =
                actionableStrategyRepository.findByCompanyCompatibility_Id(saved.getId())
                        .map(e -> {
                            List<String> keywords = actionableKeywordRepository
                                    .findByActionableStrategy_IdOrderByDisplayOrderAsc(e.getId())
                                    .stream().map(ActionableKeyword::getKeyword).toList();
                            List<String> luckyDays = luckyDayRepository
                                    .findByActionableStrategy_IdOrderByDisplayOrderAsc(e.getId())
                                    .stream().map(LuckyDay::getLuckyDay).toList();
                            return new CompatibilityResponse.ActionableStrategy(
                                    keywords, e.getWeaknessDefense(),
                                    new CompatibilityResponse.ActionableStrategy.BestTiming(
                                            luckyDays, e.getPreferredTime()));
                        })
                        .orElse(null);

        List<ExpectedInterviewQuestion> questions =
                expectedInterviewQuestionRepository.findByCompanyCompatibility_Id(saved.getId());
        List<RoleCompatibility> roles =
                roleCompatibilityRepository.findByCompanyCompatibility_Id(saved.getId());
        List<MonthlyForecast> forecasts =
                monthlyForecastRepository.findByCompanyCompatibility_Id(saved.getId());
        List<Caution> cautionList =
                cautionRepository.findByCompanyCompatibility_Id(saved.getId());

        return new CompatibilityResponse(
                buildRequestContext(saved, request),
                saved.getCompatibilityScore(),
                saved.getSummary(),
                targetRoleAnalysis,
                fiveElements,
                analysisBreakdown,
                actionableStrategy,
                questions.stream().map(q -> new CompatibilityResponse.InterviewQuestion(
                        q.getQuestion(), q.getIntent())).toList(),
                roles.stream().map(r -> new CompatibilityResponse.RoleCompatibility(
                        r.getRoleName(), r.getScore(), r.getReason(), r.getTag())).toList(),
                forecasts.stream().map(f -> new CompatibilityResponse.MonthlyForecast(
                        f.getMonth(), f.getScore(), f.getStatus(), f.getAdvice())).toList(),
                cautionList.stream().map(Caution::getContent).toList()
        );
    }

    private CompatibilityResponse buildNewResponse(CompanyCompatibility saved,
                                                     CompatibilityRequest request,
                                                     CompatibilityAnalysisData data) {
        CompatibilityAnalysisData.StrategyInfo s = data.strategy();
        return new CompatibilityResponse(
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
