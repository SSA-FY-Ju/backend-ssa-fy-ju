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
 * CompanyMatchingService에서 Repository 11개 직접 주입 문제(M-2)를 해결하기 위해 분리.
 */
@Service
@RequiredArgsConstructor
public class CompatibilityChildReadService {

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
     * 두 buildFromExisting 오버로드가 공유하는 핵심 로직.
     */
    private CompatibilityResponse buildResponseWithContext(CompanyCompatibility saved,
                                                           CompatibilityResponse.RequestContext requestContext) {
        CompatibilityResponse.TargetRoleAnalysis targetRoleAnalysis =
                targetRoleAnalysisRepository.findByCompanyCompatibility_Id(saved.getId())
                        .map(e -> new CompatibilityResponse.TargetRoleAnalysis(
                                e.getMatchScore(), e.getSynergy(), e.getWarning()))
                        .orElseThrow(() -> new DataAccessException(
                                "completed=true인데 TargetRoleAnalysis가 없음: id=" + saved.getId()));

        CompatibilityResponse.FiveElements fiveElements =
                fiveElementsAnalysisRepository.findByCompanyCompatibility_Id(saved.getId())
                        .map(e -> new CompatibilityResponse.FiveElements(
                                e.getUserDistribution(), e.getCompanyDistribution(), e.getSynergyDescription()))
                        .orElseThrow(() -> new DataAccessException(
                                "completed=true인데 FiveElementsAnalysis가 없음: id=" + saved.getId()));

        CompatibilityResponse.AnalysisBreakdown analysisBreakdown =
                analysisBreakdownRepository.findByCompanyCompatibility_Id(saved.getId())
                        .map(e -> new CompatibilityResponse.AnalysisBreakdown(
                                e.getCharacterMatch(), e.getPotentialSynergy(), e.getLongTermStability()))
                        .orElseThrow(() -> new DataAccessException(
                                "completed=true인데 AnalysisBreakdown이 없음: id=" + saved.getId()));

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
                        .orElseThrow(() -> new DataAccessException(
                                "completed=true인데 ActionableStrategy가 없음: id=" + saved.getId()));

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
