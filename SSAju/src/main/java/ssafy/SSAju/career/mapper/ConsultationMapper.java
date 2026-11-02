package ssafy.SSAju.career.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.career.domain.TenGodHiddenStemAnalysis;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.CareerFortune;
import ssafy.SSAju.career.entity.SajuFullData;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.enums.TenGodConstants;
import ssafy.SSAju.career.provider.SajuAnalysisFacade;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.exception.DataAccessException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ConsultationMapper {

    /** KST 기준 현재 연도 계산용 Clock. 테스트에서 고정 시각 주입 가능. */
    private final Clock clock;

    /**
     * CareerAdviceResponse 전체를 CareerConsultation으로 변환.
     */
    public CareerConsultation buildConsultation(SajuResult sajuResult,
                                                CareerAdviceResponse advice,
                                                String modelVersion,
                                                Integer consultationMonth) {
        return CareerConsultation.builder()
                .sajuResult(sajuResult)
                .openaiModelVersion(modelVersion)
                .consultationMonth(consultationMonth)
                .resultJson(advice)
                .build();
    }

    /**
     * 모델 버전 변경 시 기존 CareerConsultation을 새 분석 결과로 갱신.
     */
    public void updateConsultation(CareerConsultation existing, CareerAdviceResponse advice, String modelVersion) {
        existing.updateData(modelVersion, advice);
    }

    /**
     * 저장된 CareerConsultation에서 CareerAdviceResponse 복원 (캐시 히트 시 사용).
     */
    public CareerAdviceResponse restoreAdvice(CareerConsultation cc) {
        CareerAdviceResponse advice = cc.getResultJson();
        if (advice == null) {
            throw new DataAccessException("CareerConsultation.resultJson이 없습니다: id=" + cc.getId());
        }
        return advice;
    }

    public ConsultationResponse toResponse(FastAPIResponse sajuData,
                                            TenGodDistribution tenGodDistribution,
                                            String dayMaster,
                                            String favoredPeriod,
                                            int confidenceScore,
                                            String reasoning,
                                            SajuResult sajuResult,
                                            Long consultationId,
                                            CareerAdviceResponse advice,
                                            String modelVersion) {
        Map<String, String> tenGodCharacteristics = buildTenGodCharacteristics(tenGodDistribution);
        ConsultationResponse.SajuProfile sajuProfile = new ConsultationResponse.SajuProfile(
                dayMaster,
                advice.dayMasterDescription(),
                sajuData.fiveElements(),
                advice.fiveElementsAnalysis(),
                tenGodDistribution.asMap(),
                advice.keyTenGods(),
                tenGodCharacteristics
        );
        String analysisSummary = buildAnalysisSummary(
                dayMaster, tenGodDistribution, sajuData.fiveElements(), favoredPeriod);

        return new ConsultationResponse(
                consultationId,
                advice.industries(),
                advice.interviewTips(),
                advice.strengths(),
                modelVersion,
                favoredPeriod,
                confidenceScore,
                reasoning,
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
                analysisSummary
        );
    }

    /**
     * 마이페이지 상세 조회 전용: DB에 저장된 CareerConsultation 엔티티로 ConsultationResponse 복원.
     * FastAPI 재호출 없이 SajuResult에 저장된 사주 데이터를 재사용합니다.
     */
    public ConsultationResponse toResponseFromEntity(CareerConsultation cc) {
        SajuResult sajuResult = cc.getSajuResult();
        SajuFullData sfd = sajuResult.getSajuFullData();
        CareerFortune cf = sajuResult.getCareerFortune();

        String dayMaster = sfd != null ? sfd.getDayMaster() : "";
        Map<String, Integer> fiveElements = sfd != null ? sfd.getFiveElements() : Map.of();

        TenGodHiddenStemAnalysis analysis = sajuResult.getTenGodHiddenStemAnalysis();
        Map<String, Integer> tenGodMap = analysis != null ? analysis.tenGods() : Map.of();
        TenGodDistribution tenGodDistribution = new TenGodDistribution(tenGodMap);

        String favoredPeriod = cf != null ? cf.getFavoredPeriod() : null;
        int confidenceScore = cf != null ? cf.getConfidenceScore() : 0;
        String reasoning = cf != null ? cf.getReasoning() : null;

        CareerAdviceResponse advice = restoreAdvice(cc);

        Map<String, String> tenGodCharacteristics = buildTenGodCharacteristics(tenGodDistribution);
        ConsultationResponse.SajuProfile sajuProfile = new ConsultationResponse.SajuProfile(
                dayMaster,
                advice.dayMasterDescription(),
                fiveElements,
                advice.fiveElementsAnalysis(),
                tenGodMap,
                advice.keyTenGods(),
                tenGodCharacteristics
        );

        String analysisSummary = buildAnalysisSummary(dayMaster, tenGodDistribution, fiveElements, favoredPeriod);

        return new ConsultationResponse(
                cc.getId(),
                advice.industries(),
                advice.interviewTips(),
                advice.strengths(),
                cc.getOpenaiModelVersion(),
                favoredPeriod,
                confidenceScore,
                reasoning,
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
                analysisSummary
        );
    }

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
