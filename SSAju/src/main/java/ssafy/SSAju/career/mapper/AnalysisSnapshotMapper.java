package ssafy.SSAju.career.mapper;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.dto.response.CompatibilityResponse;

/**
 * 분석 결과와 저장 스냅샷 사이의 변환을 담당한다.
 *
 * <p>전략은 흐름 제어와 계산에 집중하고, 응답 record 모양으로 옮겨 담는 일은 이 매퍼가 맡는다.
 */
@Component
public class AnalysisSnapshotMapper {

    /**
     * 궁합 분석 결과를 완성 스냅샷으로 옮긴다. 조회 시 요청 DTO·회사 재조회·현재 계산기에
     * 의존하지 않도록 응답에 필요한 모든 값을 이 시점에 담는다(분석 ID 제외).
     */
    public AnalysisPayload.CompatibilityResultData toCompatibilityResult(
            String companyName,
            CompatibilityResponse.TargetRoleInfo targetRole,
            int compatibilityScore,
            String summary,
            CompatibilityAnalysisData data) {

        CompatibilityAnalysisData.StrategyInfo strategy = data.strategy();

        return new AnalysisPayload.CompatibilityResultData(
                AnalysisPayload.SCHEMA_VERSION,
                new CompatibilityResponse.RequestContext(companyName, targetRole),
                compatibilityScore,
                summary,
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
                        strategy.keywords(),
                        strategy.weaknessDefense(),
                        new CompatibilityResponse.ActionableStrategy.BestTiming(
                                strategy.luckyDays(), strategy.preferredTime())),
                data.questions().stream()
                        .map(q -> new CompatibilityResponse.InterviewQuestion(q.question(), q.intent()))
                        .toList(),
                data.roles().stream()
                        .map(r -> new CompatibilityResponse.RoleCompatibility(
                                r.roleName(), r.score(), r.reason(), r.tag()))
                        .toList(),
                data.forecasts().stream()
                        .map(f -> new CompatibilityResponse.MonthlyForecast(
                                f.month(), f.score(), f.status(), f.advice()))
                        .toList(),
                data.cautions());
    }
}
