package ssafy.SSAju.career.mapper;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.AnalysisExecution;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.dto.response.AnalysisDetailResponse;
import ssafy.SSAju.dto.response.CareerTimingResponse;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.LocalDate;

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

    /**
     * 저장된 결과 스냅샷에 컬럼에서 읽은 숫자 ID를 붙여 관운 응답을 만든다.
     * 기존 응답의 필드명과 Long/JSON 숫자 타입을 그대로 유지한다.
     */
    public CareerTimingResponse toCareerTimingResponse(AnalysisExecution execution) {
        AnalysisPayload.SajuResultData data = require(execution, AnalysisPayload.SajuResultData.class);
        return new CareerTimingResponse(
                execution.analysisId(), data.favoredPeriod(), data.confidenceScore(), data.reasoning());
    }

    public ConsultationResponse toConsultationResponse(AnalysisExecution execution) {
        AnalysisPayload.ConsultationResultData data =
                require(execution, AnalysisPayload.ConsultationResultData.class);
        return new ConsultationResponse(
                execution.analysisId(),
                data.industries(),
                data.interviewTips(),
                data.strengths(),
                data.openaiModelVersion(),
                data.favoredPeriod(),
                data.confidenceScore(),
                data.reasoning(),
                data.sajuProfile(),
                data.cautions(),
                data.wealthStyle(),
                data.longTermRoadmap(),
                data.personalBranding(),
                data.powerKeywords(),
                data.mentalCare(),
                data.environmentFit(),
                data.workStyle(),
                data.relationshipStrategy(),
                data.careerTimeline(),
                data.analysisSummary());
    }

    public CompatibilityResponse toCompatibilityResponse(AnalysisExecution execution) {
        AnalysisPayload.CompatibilityResultData data =
                require(execution, AnalysisPayload.CompatibilityResultData.class);
        return new CompatibilityResponse(
                execution.analysisId(),
                data.requestContext(),
                data.compatibilityScore(),
                data.summary(),
                data.targetRoleAnalysis(),
                data.fiveElements(),
                data.analysisBreakdown(),
                data.actionableStrategy(),
                data.expectedInterviewQuestions(),
                data.roleCompatibility(),
                data.monthlyForecast(),
                data.cautions());
    }

    /** 유형과 저장된 스냅샷 record가 어긋나면 결과를 만들지 않고 기존 오류 체계로 실패한다. */
    private <T extends AnalysisPayload.Result> T require(AnalysisExecution execution, Class<T> type) {
        if (!type.isInstance(execution.result())) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_TYPE_UNSUPPORTED.getMessage());
        }
        return type.cast(execution.result());
    }

    /**
     * 저장된 스냅샷만으로 마이페이지 상세 응답을 만든다. 현재 연도·모델·회사 조회에 의존하지 않고
     * 외부 호출이나 새 분석도 하지 않는다 — 조회는 저장된 내용을 그대로 돌려준다.
     *
     * @param birthDate 분석에 실제 사용한 출생일(saju → manse → user_profile)
     */
    public AnalysisDetailResponse toDetailResponse(Saju saju,
                                                    String userName,
                                                    LocalDate birthDate,
                                                    AnalysisPayload.Input input,
                                                    AnalysisPayload.Result result) {
        AnalysisType type = saju.getAnalysisType();
        AnalysisExecution execution = new AnalysisExecution(saju.getId(), result);

        return switch (type) {
            case SAJU -> {
                AnalysisPayload.SajuResultData data = require(execution, AnalysisPayload.SajuResultData.class);
                yield new AnalysisDetailResponse(
                        type.name(), saju.getId(), userName, birthDate, saju.getCreatedAt(),
                        null, null,
                        new AnalysisDetailResponse.CareerFortuneDetail(
                                data.favoredPeriod(), data.confidenceScore(), data.reasoning()),
                        null, null);
            }
            case CAREER_CONSULTATION -> new AnalysisDetailResponse(
                    type.name(), saju.getId(), userName, birthDate, saju.getCreatedAt(),
                    null, null, null, toConsultationResponse(execution), null);
            case COMPANY_COMPATIBILITY -> new AnalysisDetailResponse(
                    type.name(), saju.getId(), companyNameOf(input), birthDate, saju.getCreatedAt(),
                    null, null, null, null, toCompatibilityResponse(execution));
        };
    }

    private String companyNameOf(AnalysisPayload.Input input) {
        if (!(input instanceof AnalysisPayload.CompatibilityInput compatibilityInput)) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_TYPE_UNSUPPORTED.getMessage());
        }
        return compatibilityInput.companyName();
    }
}
