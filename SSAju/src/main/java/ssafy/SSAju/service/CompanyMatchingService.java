package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.SSAju.career.domain.FiveElements;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.entity.*;
import ssafy.SSAju.career.enums.ForecastStatus;
import ssafy.SSAju.career.enums.SajuPillarIndex;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.util.*;
import ssafy.SSAju.career.validator.SajuValidator;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.repository.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 기업/직무 궁합 분석 서비스.
 *
 * @Transactional 없음: FastAPI 외부 I/O 동안 DB 커넥션을 점유하지 않도록 트랜잭션을 분리.
 * 각 DB 작업은 Repository의 @Transactional에 의해 개별 트랜잭션으로 실행됨.
 * INSERT IGNORE + UNIQUE 제약으로 동시 요청 Race Condition을 안전하게 처리함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyMatchingService {

    private static final LocalTime DEFAULT_FOUNDING_TIME = LocalTime.of(12, 0);

    private final SajuDataService sajuDataService;
    private final UserProfileProvider userProfileProvider;
    private final SajuValidator sajuValidator;
    private final TenGodCalculator tenGodCalculator;
    private final HiddenStemCalculator hiddenStemCalculator;
    private final CompatibilityScoreCalculator compatibilityScoreCalculator;
    private final JobRoleAnalyzer jobRoleAnalyzer;

    private final CompanyCompatibilityRepository companyCompatibilityRepository;
    private final CompanyCompatibilityJdbcRepository companyCompatibilityJdbcRepository;
    private final TargetRoleAnalysisRepository targetRoleAnalysisRepository;
    private final FiveElementsAnalysisRepository fiveElementsAnalysisRepository;
    private final AnalysisBreakdownRepository analysisBreakdownRepository;
    private final ActionableStrategyRepository actionableStrategyRepository;
    private final ExpectedInterviewQuestionRepository expectedInterviewQuestionRepository;
    private final RoleCompatibilityRepository roleCompatibilityRepository;
    private final MonthlyForecastRepository monthlyForecastRepository;
    private final CautionRepository cautionRepository;

    public CompatibilityResponse analyzeCompatibility(CompatibilityRequest request) {
        log.info("기업 궁합 분석 시작");

        UserProfile userProfile = userProfileProvider.findOrCreate(
                request.userBirthDate(), resolveUserBirthTime(request));

        // 사용자 사주 조회 및 계산
        FastAPIResponse userSaju = sajuDataService.fetchSajuFromFastAPI(
                request.userBirthDate(), resolveUserBirthTime(request));
        sajuValidator.validate(userSaju);

        HiddenStems userHiddenStems = hiddenStemCalculator.calculate(userSaju.earthlyBranches());
        String userDayMaster = userSaju.heavenlyStems().get(SajuPillarIndex.DAY_INDEX);
        FiveElements userFiveElements = new FiveElements(userSaju.fiveElements());

        // 기업 설립일 사주 조회 및 계산 (시간 미상 시 12:00 기본값)
        LocalDate companyDate = request.companyFoundingDate();
        LocalTime companyTime = request.companyFoundingTime() != null
                ? request.companyFoundingTime() : DEFAULT_FOUNDING_TIME;

        FastAPIResponse companySaju = sajuDataService.fetchSajuFromFastAPI(companyDate, companyTime);
        sajuValidator.validate(companySaju);

        HiddenStems companyHiddenStems = hiddenStemCalculator.calculate(companySaju.earthlyBranches());
        String companyDayMaster = companySaju.heavenlyStems().get(SajuPillarIndex.DAY_INDEX);
        FiveElements companyFiveElements = new FiveElements(companySaju.fiveElements());

        // 궁합 점수 계산
        int compatibilityScore = compatibilityScoreCalculator.calculate(
                userHiddenStems, userDayMaster, companyHiddenStems, companyDayMaster);

        // 직군 오행 분석
        CompatibilityResponse.TargetRoleAnalysis targetRoleAnalysis =
                jobRoleAnalyzer.analyze(userFiveElements, request.targetRole().category());

        // 파생 분석 생성
        CompatibilityResponse.FiveElements fiveElementsData = buildFiveElementsData(
                userFiveElements, companyFiveElements);
        CompatibilityResponse.AnalysisBreakdown analysisBreakdown =
                buildAnalysisBreakdown(compatibilityScore);
        CompatibilityResponse.ActionableStrategy actionableStrategy =
                buildActionableStrategy(request.targetRole().category());
        List<CompatibilityResponse.InterviewQuestion> questions = buildInterviewQuestions(
                request.targetRole().category());
        List<CompatibilityResponse.RoleCompatibility> roleCompatibilities =
                buildRoleCompatibilities(request.targetRole().category(), userFiveElements);
        List<CompatibilityResponse.MonthlyForecast> monthlyForecasts = buildMonthlyForecasts();
        List<String> cautions = buildCautions(userFiveElements, request.targetRole().category());
        String summary = buildSummary(compatibilityScore, request.targetRole().category());

        // INSERT IGNORE로 루트 엔티티 저장 (Race Condition 안전 처리)
        CompanyCompatibility root = CompanyCompatibility.builder()
                .userProfile(userProfile)
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
                .orElseThrow(() -> new ssafy.SSAju.exception.DataAccessException("CompanyCompatibility 조회 실패"));

        if (inserted == 0) {
            // 이미 존재 → 기존 DB 결과 재사용 (AI 재호출 없음)
            log.info("기존 궁합 분석 결과 재사용 (compatibilityId={})", saved.getId());
            return buildResponseFromExisting(saved, request);
        }

        // 신규 삽입: 모든 자식 엔티티 저장
        saveAllChildren(saved, targetRoleAnalysis, fiveElementsData, analysisBreakdown,
                actionableStrategy, questions, roleCompatibilities, monthlyForecasts, cautions);

        log.info("기업 궁합 분석 완료: compatibilityScore={}", compatibilityScore);
        return buildResponse(saved, request, targetRoleAnalysis, fiveElementsData,
                analysisBreakdown, actionableStrategy, questions, roleCompatibilities,
                monthlyForecasts, cautions);
    }

    private LocalTime resolveUserBirthTime(CompatibilityRequest request) {
        return request.userBirthTime() != null ? request.userBirthTime() : DEFAULT_FOUNDING_TIME;
    }

    private void saveAllChildren(CompanyCompatibility saved,
                                  CompatibilityResponse.TargetRoleAnalysis roleAnalysis,
                                  CompatibilityResponse.FiveElements fiveElementsData,
                                  CompatibilityResponse.AnalysisBreakdown breakdown,
                                  CompatibilityResponse.ActionableStrategy strategy,
                                  List<CompatibilityResponse.InterviewQuestion> questions,
                                  List<CompatibilityResponse.RoleCompatibility> roles,
                                  List<CompatibilityResponse.MonthlyForecast> forecasts,
                                  List<String> cautions) {
        targetRoleAnalysisRepository.save(TargetRoleAnalysis.builder()
                .companyCompatibility(saved)
                .matchScore(roleAnalysis.matchScore())
                .synergy(roleAnalysis.synergy())
                .warning(roleAnalysis.warning())
                .build());

        fiveElementsAnalysisRepository.save(FiveElementsAnalysis.builder()
                .companyCompatibility(saved)
                .userDistribution(fiveElementsData.userDistribution())
                .companyDistribution(fiveElementsData.companyDistribution())
                .synergyDescription(fiveElementsData.synergyDescription())
                .build());

        analysisBreakdownRepository.save(AnalysisBreakdown.builder()
                .companyCompatibility(saved)
                .characterMatch(breakdown.characterMatch())
                .potentialSynergy(breakdown.potentialSynergy())
                .longTermStability(breakdown.longTermStability())
                .build());

        actionableStrategyRepository.save(ActionableStrategy.builder()
                .companyCompatibility(saved)
                .interviewKeywords(strategy.interviewKeywords())
                .weaknessDefense(strategy.weaknessDefense())
                .luckyDays(strategy.bestTiming().luckyDays())
                .preferredTime(strategy.bestTiming().preferredTime())
                .build());

        questions.forEach(q -> expectedInterviewQuestionRepository.save(
                ExpectedInterviewQuestion.builder()
                        .companyCompatibility(saved)
                        .question(q.question())
                        .intent(q.intent())
                        .build()));

        roles.forEach(r -> roleCompatibilityRepository.save(
                RoleCompatibility.builder()
                        .companyCompatibility(saved)
                        .roleName(r.roleName())
                        .score(r.score())
                        .reason(r.reason())
                        .tag(r.tag())
                        .build()));

        forecasts.forEach(f -> monthlyForecastRepository.save(
                MonthlyForecast.builder()
                        .companyCompatibility(saved)
                        .month(f.month())
                        .score(f.score())
                        .status(f.status())
                        .advice(f.advice())
                        .build()));

        cautions.forEach(c -> cautionRepository.save(
                Caution.builder()
                        .companyCompatibility(saved)
                        .content(c)
                        .build()));
    }

    private CompatibilityResponse buildResponseFromExisting(CompanyCompatibility saved,
                                                              CompatibilityRequest request) {
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
                null,
                null,
                null,
                null,
                questions.stream().map(q -> new CompatibilityResponse.InterviewQuestion(
                        q.getQuestion(), q.getIntent())).toList(),
                roles.stream().map(r -> new CompatibilityResponse.RoleCompatibility(
                        r.getRoleName(), r.getScore(), r.getReason(), r.getTag())).toList(),
                forecasts.stream().map(f -> new CompatibilityResponse.MonthlyForecast(
                        f.getMonth(), f.getScore(), f.getStatus(), f.getAdvice())).toList(),
                cautionList.stream().map(Caution::getContent).toList()
        );
    }

    private CompatibilityResponse buildResponse(CompanyCompatibility saved,
                                                  CompatibilityRequest request,
                                                  CompatibilityResponse.TargetRoleAnalysis roleAnalysis,
                                                  CompatibilityResponse.FiveElements fiveElements,
                                                  CompatibilityResponse.AnalysisBreakdown breakdown,
                                                  CompatibilityResponse.ActionableStrategy strategy,
                                                  List<CompatibilityResponse.InterviewQuestion> questions,
                                                  List<CompatibilityResponse.RoleCompatibility> roles,
                                                  List<CompatibilityResponse.MonthlyForecast> forecasts,
                                                  List<String> cautions) {
        return new CompatibilityResponse(
                buildRequestContext(saved, request),
                saved.getCompatibilityScore(),
                saved.getSummary(),
                roleAnalysis,
                fiveElements,
                breakdown,
                strategy,
                questions,
                roles,
                forecasts,
                cautions
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

    private CompatibilityResponse.FiveElements buildFiveElementsData(FiveElements user,
                                                                       FiveElements company) {
        String synergy = buildElementSynergyText(user, company);
        return new CompatibilityResponse.FiveElements(user.asMap(), company.asMap(), synergy);
    }

    private String buildElementSynergyText(FiveElements user, FiveElements company) {
        List<String> elements = List.of("木", "火", "土", "金", "水");
        for (String elem : elements) {
            if (user.getCount(elem) == 0 && company.getCount(elem) > 0) {
                return String.format(
                        "기업의 강한 '%s' 기운이 사용자의 부족한 오행을 보완하는 상생 구조입니다.", elem);
            }
        }
        return "사용자와 기업의 오행 분포가 균형 잡혀 안정적인 궁합을 보입니다.";
    }

    private CompatibilityResponse.AnalysisBreakdown buildAnalysisBreakdown(int totalScore) {
        int characterMatch = Math.min(totalScore + 5, 100);
        int potentialSynergy = Math.max(totalScore - 5, 0);
        int longTermStability = totalScore;
        return new CompatibilityResponse.AnalysisBreakdown(characterMatch, potentialSynergy, longTermStability);
    }

    private CompatibilityResponse.ActionableStrategy buildActionableStrategy(JobCategoryEnum category) {
        List<String> keywords = List.of("체계적 설계", "논리적 사고", "안정적 실행");
        String weaknessDefense = String.format(
                "%s 분야 관련 약점 질문 시, 지속적인 학습과 성장 의지를 강조하세요.",
                category.getDisplayName());
        List<String> luckyDays = List.of(
                LocalDate.now().plusDays(7).toString(),
                LocalDate.now().plusDays(14).toString(),
                LocalDate.now().plusDays(21).toString()
        );
        String preferredTime = "오전 09:00 ~ 11:00";
        return new CompatibilityResponse.ActionableStrategy(
                keywords, weaknessDefense,
                new CompatibilityResponse.ActionableStrategy.BestTiming(luckyDays, preferredTime)
        );
    }

    private List<CompatibilityResponse.InterviewQuestion> buildInterviewQuestions(JobCategoryEnum category) {
        return List.of(
                new CompatibilityResponse.InterviewQuestion(
                        String.format("%s 분야에서 가장 도전적인 문제를 해결한 경험을 말씀해주세요.",
                                category.getDisplayName()),
                        "문제 해결 능력과 직군 전문성 검증"
                ),
                new CompatibilityResponse.InterviewQuestion(
                        "팀 내 갈등 상황에서 어떻게 대처하셨나요?",
                        "협업 능력 및 대인관계 역량 평가"
                )
        );
    }

    private List<CompatibilityResponse.RoleCompatibility> buildRoleCompatibilities(
            JobCategoryEnum category, FiveElements userFiveElements) {
        int primaryScore = Math.min(userFiveElements.getCount(category.getPrimaryElement()) * 30 + 40, 100);
        int secondaryScore = Math.max(primaryScore - 15, 0);

        String primaryTag = primaryScore >= 75 ? "강력 추천" : "보통";
        String secondaryTag = secondaryScore >= 60 ? "보통" : "신중 검토";

        return List.of(
                new CompatibilityResponse.RoleCompatibility(
                        category.getDisplayName() + " 전문가",
                        primaryScore,
                        String.format("%s 오행 기반 적성이 높습니다.", category.getPrimaryElement()),
                        primaryTag
                ),
                new CompatibilityResponse.RoleCompatibility(
                        category.getDisplayName() + " 리드",
                        secondaryScore,
                        "리더십 역량과 기술 전문성을 함께 요구합니다.",
                        secondaryTag
                )
        );
    }

    private List<CompatibilityResponse.MonthlyForecast> buildMonthlyForecasts() {
        int currentMonth = LocalDate.now().getMonthValue();
        return List.of(
                new CompatibilityResponse.MonthlyForecast(currentMonth, 75, ForecastStatus.LUCKY,
                        "적극적인 지원 시기입니다."),
                new CompatibilityResponse.MonthlyForecast((currentMonth % 12) + 1, 50, ForecastStatus.NORMAL,
                        "역량 강화에 집중하세요."),
                new CompatibilityResponse.MonthlyForecast(((currentMonth + 1) % 12) + 1, 85, ForecastStatus.LUCKY,
                        "면접 성과가 기대되는 시기입니다."),
                new CompatibilityResponse.MonthlyForecast(((currentMonth + 2) % 12) + 1, 40, ForecastStatus.CAUTION,
                        "신중한 결정이 필요한 시기입니다."),
                new CompatibilityResponse.MonthlyForecast(((currentMonth + 3) % 12) + 1, 65, ForecastStatus.NORMAL,
                        "꾸준한 준비가 결실을 맺는 시기입니다.")
        );
    }

    private List<String> buildCautions(FiveElements userFiveElements, JobCategoryEnum category) {
        return List.of(
                String.format("%s 분야의 빠른 변화 속도에 적응하는 시간이 필요할 수 있습니다.",
                        category.getDisplayName()),
                "초기 입사 후 조직 문화 적응에 시간이 다소 걸릴 수 있습니다."
        );
    }

    private String buildSummary(int score, JobCategoryEnum category) {
        if (score >= 75) {
            return String.format("'%s' 분야에서 사용자의 사주와 기업이 높은 시너지를 보이는 상생(相生)의 궁합입니다.",
                    category.getDisplayName());
        }
        if (score >= 50) {
            return String.format("'%s' 분야에서 사용자와 기업 간 균형 잡힌 궁합을 보입니다.",
                    category.getDisplayName());
        }
        return String.format("'%s' 분야에서 추가적인 역량 개발이 필요한 궁합입니다.",
                category.getDisplayName());
    }
}
