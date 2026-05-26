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
import ssafy.SSAju.repository.CompanyCompatibilityJdbcRepository;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

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
    // 레포지토리 / 자식 서비스
    // ─────────────────────────────────────────
    private final CompanyCompatibilityRepository companyCompatibilityRepository;
    private final CompanyCompatibilityJdbcRepository companyCompatibilityJdbcRepository;
    private final CompatibilityChildSaveService childSaveService;
    private final CompatibilityChildReadService childReadService;
    private final UserRepository userRepository;

    public CompatibilityResponse analyzeCompatibility(CompatibilityRequest request, Long userId) {
        log.info("기업 궁합 분석 시작");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        // ─── 이번 달 캐시 조회 (FastAPI 호출 이전에 확인) ──────────────
        LocalTime userBirthTime = resolveUserBirthTime(request);
        UserProfile userProfile = userProfileProvider.findOrCreate(
                request.userBirthDate(), userBirthTime);

        YearMonth now = YearMonth.now();
        Integer compatibilityMonth = now.getYear() * 100 + now.getMonthValue();

        Optional<CompanyCompatibility> cachedOpt = companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        userId, userProfile.getId(),
                        request.companyName(), request.targetRole().category(), compatibilityMonth);

        if (cachedOpt.isPresent() && cachedOpt.get().isCompleted()) {
            log.info("이번 달 궁합 분석 캐시 히트 (compatibilityId={})", cachedOpt.get().getId());
            return childReadService.buildFromExisting(cachedOpt.get(), request);
        }

        // ─── 사용자 사주 계산 ──────────────────────────────────────
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
        // 캐시 미스: INSERT IGNORE로 root 엔티티 삽입
        //
        // - inserted=1: 신규 → 자식 엔티티들 저장
        // - inserted=0: 동시 요청이 먼저 삽입함 → 재조회 후 분기
        //   - completed=true: 자식 저장 완료된 캐시 재사용
        //   - completed=false: 자식 저장 진행 중 → 현재 계산 결과로 응답
        // ───────────────────────────────────────────────────────────────────
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

        int inserted = companyCompatibilityJdbcRepository.insertOrIgnore(root);

        CompanyCompatibility saved = companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        userId, userProfile.getId(),
                        request.companyName(), request.targetRole().category(), compatibilityMonth)
                .orElseThrow(() -> new ssafy.SSAju.exception.DataAccessException(
                        "CompanyCompatibility 조회 실패"));

        if (inserted == 0) {
            if (saved.isCompleted()) {
                // completed=true → 자식 데이터가 완전히 저장된 캐시만 재사용
                log.info("완료된 궁합 분석 캐시 재사용 (compatibilityId={})", saved.getId());
                return childReadService.buildFromExisting(saved, request);
            }
            // completed=false: 다른 요청이 자식 저장 진행 중 → 현재 계산 결과로 응답
            log.info("자식 저장 진행 중인 기존 레코드 감지 (compatibilityId={}), 현재 계산 결과로 응답",
                    saved.getId());
            return buildNewResponse(saved, request, analysisData);
        }

        // 자식 엔티티 전체 저장을 단일 트랜잭션(REQUIRES_NEW)으로 위임
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
