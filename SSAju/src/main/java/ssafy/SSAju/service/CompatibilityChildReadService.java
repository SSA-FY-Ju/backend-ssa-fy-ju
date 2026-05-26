package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.*;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.repository.*;

import java.util.List;

/**
 * 기업 궁합 캐시 재사용 경로에서 자식 엔티티를 DB에서 로드하여 응답을 구성합니다.
 *
 * <p><strong>쿼리 최적화 구조 (10개 → 7개 SELECT)</strong>:
 * <ol>
 *   <li>1개: {@link CompanyCompatibilityRepository#findByIdWithOneToOneChildren}로
 *       TargetRoleAnalysis, FiveElementsAnalysis, AnalysisBreakdown, ActionableStrategy 동시 fetch join</li>
 *   <li>2개: ActionableKeyword, LuckyDay (ActionableStrategy id 기준 각 1개)</li>
 *   <li>4개: ExpectedInterviewQuestion, RoleCompatibility, MonthlyForecast, Caution
 *       (다중 컬렉션 동시 JOIN FETCH는 Cartesian product 위험 → 개별 쿼리 유지)</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CompatibilityChildReadService {

    private final CompanyCompatibilityRepository companyCompatibilityRepository;
    private final ActionableKeywordRepository actionableKeywordRepository;
    private final LuckyDayRepository luckyDayRepository;
    private final ExpectedInterviewQuestionRepository expectedInterviewQuestionRepository;
    private final RoleCompatibilityRepository roleCompatibilityRepository;
    private final MonthlyForecastRepository monthlyForecastRepository;
    private final CautionRepository cautionRepository;

    @Transactional(readOnly = true)
    public CompatibilityResponse buildFromExisting(CompanyCompatibility saved,
                                                    CompatibilityRequest request) {
        CompatibilityResponse.RequestContext requestContext = buildRequestContext(saved, request);
        return buildResponseWithContext(saved, requestContext);
    }

    /**
     * 마이페이지 상세 조회 전용: CompatibilityRequest 없이 엔티티의 저장된 데이터로 응답 구성.
     */
    @Transactional(readOnly = true)
    public CompatibilityResponse buildFromExisting(CompanyCompatibility saved) {
        CompatibilityResponse.RequestContext requestContext = new CompatibilityResponse.RequestContext(
                saved.getCompanyName(),
                new CompatibilityResponse.TargetRoleInfo(
                        saved.getTargetRoleCategory(),
                        saved.getTargetRoleDetailName()
                )
        );
        return buildResponseWithContext(saved, requestContext);
    }

    /**
     * RequestContext를 제외한 공통 자식 엔티티 로드 및 응답 조립.
     *
     * <p>1:1 자식 4개는 {@link CompanyCompatibilityRepository#findByIdWithOneToOneChildren}으로
     * 단일 쿼리에서 fetch join하여 로드. 컬렉션 자식은 별도 쿼리로 조회.
     */
    private CompatibilityResponse buildResponseWithContext(CompanyCompatibility saved,
                                                           CompatibilityResponse.RequestContext requestContext) {
        // ─── 1:1 자식 4개 단일 쿼리 fetch join ─────────────────────────────
        CompanyCompatibility loaded = companyCompatibilityRepository
                .findByIdWithOneToOneChildren(saved.getId())
                .orElseThrow(() -> new DataAccessException(
                        "CompanyCompatibility 재조회 실패: id=" + saved.getId()));

        TargetRoleAnalysis targetRoleAnalysisEntity = loaded.getTargetRoleAnalysis();
        if (targetRoleAnalysisEntity == null) {
            throw new DataAccessException(
                    "completed=true인데 TargetRoleAnalysis가 없음: id=" + saved.getId());
        }
        CompatibilityResponse.TargetRoleAnalysis targetRoleAnalysis =
                new CompatibilityResponse.TargetRoleAnalysis(
                        targetRoleAnalysisEntity.getMatchScore(),
                        targetRoleAnalysisEntity.getSynergy(),
                        targetRoleAnalysisEntity.getWarning());

        FiveElementsAnalysis fiveElementsEntity = loaded.getFiveElementsAnalysis();
        if (fiveElementsEntity == null) {
            throw new DataAccessException(
                    "completed=true인데 FiveElementsAnalysis가 없음: id=" + saved.getId());
        }
        CompatibilityResponse.FiveElements fiveElements =
                new CompatibilityResponse.FiveElements(
                        fiveElementsEntity.getUserDistribution(),
                        fiveElementsEntity.getCompanyDistribution(),
                        fiveElementsEntity.getSynergyDescription());

        AnalysisBreakdown analysisBreakdownEntity = loaded.getAnalysisBreakdown();
        if (analysisBreakdownEntity == null) {
            throw new DataAccessException(
                    "completed=true인데 AnalysisBreakdown이 없음: id=" + saved.getId());
        }
        CompatibilityResponse.AnalysisBreakdown analysisBreakdown =
                new CompatibilityResponse.AnalysisBreakdown(
                        analysisBreakdownEntity.getCharacterMatch(),
                        analysisBreakdownEntity.getPotentialSynergy(),
                        analysisBreakdownEntity.getLongTermStability());

        ActionableStrategy strategyEntity = loaded.getActionableStrategy();
        if (strategyEntity == null) {
            throw new DataAccessException(
                    "completed=true인데 ActionableStrategy가 없음: id=" + saved.getId());
        }

        // ─── ActionableStrategy 자식 컬렉션 (전략 id 기준 각 1개 쿼리) ─────
        List<String> keywords = actionableKeywordRepository
                .findByActionableStrategy_IdOrderByDisplayOrderAsc(strategyEntity.getId())
                .stream().map(ActionableKeyword::getKeyword).toList();
        List<String> luckyDays = luckyDayRepository
                .findByActionableStrategy_IdOrderByDisplayOrderAsc(strategyEntity.getId())
                .stream().map(LuckyDay::getLuckyDay).toList();

        CompatibilityResponse.ActionableStrategy actionableStrategy =
                new CompatibilityResponse.ActionableStrategy(
                        keywords, strategyEntity.getWeaknessDefense(),
                        new CompatibilityResponse.ActionableStrategy.BestTiming(
                                luckyDays, strategyEntity.getPreferredTime()));

        // ─── CompanyCompatibility 직접 자식 컬렉션 (compatibility_id 기준 각 1개 쿼리) ─
        List<ExpectedInterviewQuestion> questions =
                expectedInterviewQuestionRepository.findByCompanyCompatibility_Id(saved.getId());
        List<RoleCompatibility> roles =
                roleCompatibilityRepository.findByCompanyCompatibility_Id(saved.getId());
        List<MonthlyForecast> forecasts =
                monthlyForecastRepository.findByCompanyCompatibility_Id(saved.getId());
        List<Caution> cautionList =
                cautionRepository.findByCompanyCompatibility_Id(saved.getId());

        return new CompatibilityResponse(
                saved.getId(),
                requestContext,
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
