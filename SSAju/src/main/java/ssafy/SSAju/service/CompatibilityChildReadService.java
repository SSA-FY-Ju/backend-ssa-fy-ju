package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.exception.DataAccessException;

/**
 * 기업 궁합 캐시 재사용 경로에서 {@code resultJson}을 역직렬화하여 응답을 구성합니다.
 *
 * <p>이전에는 8개 자식 테이블을 최대 7개 쿼리로 조회해 조립했으나, 결과가 JSON 컬럼
 * 하나로 저장되면서 root 엔티티 조회 1건만으로 전체 응답을 구성할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class CompatibilityChildReadService {

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

    private CompatibilityResponse buildResponseWithContext(CompanyCompatibility saved,
                                                           CompatibilityResponse.RequestContext requestContext) {
        CompatibilityAnalysisData data = saved.getResultJson();
        if (data == null) {
            throw new DataAccessException(
                    "completed=true인데 resultJson이 없음: id=" + saved.getId());
        }

        CompatibilityAnalysisData.StrategyInfo s = data.strategy();

        return new CompatibilityResponse(
                saved.getId(),
                requestContext,
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
