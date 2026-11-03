package ssafy.SSAju.career.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.career.domain.TenGodHiddenStemAnalysis;
import ssafy.SSAju.career.enums.TenGodConstants;
import ssafy.SSAju.career.provider.SajuAnalysisFacade;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.response.ConsultationResponse;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ConsultationMapper {

    /** KST 기준 현재 연도 계산용 Clock. 테스트에서 고정 시각 주입 가능. */
    private final Clock clock;

    private Map<String, String> buildTenGodCharacteristics(TenGodDistribution tenGodDistribution) {
        return tenGodDistribution.asMap().keySet().stream()
                .collect(Collectors.toMap(
                        name -> name,
                        name -> {
                            TenGodConstants tg = TenGodConstants.fromName(name);
                            return tg != null ? tg.getCharacteristics() : "";
                        }
                ));
    }

    /**
     * 이번 요청의 완성 결과 스냅샷을 만든다(분석 ID 제외).
     *
     * <p>요약 문구는 저장 시점에 한 번만 만들고 조회할 때 현재 연도·모델로 다시 만들지 않는다 —
     * 그래서 현재 시각이 아니라 요청 시작 시 확정된 연도를 받는다.
     */
    public AnalysisPayload.ConsultationResultData toResultSnapshot(ManseData manseData,
                                                                    SajuAnalysisFacade.SajuAnalysisContext ctx,
                                                                    CareerAdviceResponse advice,
                                                                    String modelVersion,
                                                                    int analysisYear) {
        TenGodDistribution tenGodDistribution = ctx.tenGodDistribution();
        ConsultationResponse.SajuProfile sajuProfile = new ConsultationResponse.SajuProfile(
                ctx.dayMaster(),
                advice.dayMasterDescription(),
                manseData.fiveElements(),
                advice.fiveElementsAnalysis(),
                tenGodDistribution.asMap(),
                advice.keyTenGods(),
                buildTenGodCharacteristics(tenGodDistribution)
        );

        return new AnalysisPayload.ConsultationResultData(
                AnalysisPayload.SCHEMA_VERSION,
                advice.industries(),
                advice.interviewTips(),
                advice.strengths(),
                modelVersion,
                ctx.favoredPeriod(),
                ctx.confidenceScore(),
                ctx.reasoning(),
                sajuProfile,
                advice.cautions(),
                advice.wealthStyle(),
                advice.longTermRoadmap(),
                advice.personalBranding(),
                advice.powerKeywords(),
                advice.mentalCare(),
                advice.environmentFit(),
                advice.workStyle(),
                advice.relationshipStrategy(),
                advice.careerTimeline(),
                buildAnalysisSummary(ctx.dayMaster(), tenGodDistribution, manseData.fiveElements(),
                        ctx.favoredPeriod(), analysisYear)
        );
    }

    public String buildAnalysisSummary(String dayMaster,
                                        TenGodDistribution tenGodDistribution,
                                        Map<String, Integer> fiveElements,
                                        String favoredPeriod) {
        return buildAnalysisSummary(dayMaster, tenGodDistribution, fiveElements, favoredPeriod,
                LocalDate.now(clock).getYear());
    }

    /** 요청 시작 시 확정한 연도로 요약을 만든다. 요청 처리 중 해가 바뀌어도 값이 흔들리지 않는다. */
    public String buildAnalysisSummary(String dayMaster,
                                        TenGodDistribution tenGodDistribution,
                                        Map<String, Integer> fiveElements,
                                        String favoredPeriod,
                                        int analysisYear) {
        String dominantElements = (fiveElements == null || fiveElements.isEmpty())
                ? "정보 없음"
                : fiveElements.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(2)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.joining("·"));

        int officerCount = tenGodDistribution.getScore("정관")
                + tenGodDistribution.getScore("편관");
        String tenGodSummary = officerCount > 0 ? "정관·편관 기운" : "십신 종합";

        // CareerFortune이 없는 경우(favoredPeriod=null) UI에 "(null)" 노출 방지
        String periodInfo = (favoredPeriod != null) ? favoredPeriod : "분석 미포함";
        return "%s 일간 · 오행 %s 강세 · %s 기반 | %d년 12개월 타임라인 + 관운 분석 (%s)"
                .formatted(dayMaster, dominantElements, tenGodSummary, analysisYear, periodInfo);
    }
}
