package ssafy.SSAju.dto.response;

import ssafy.SSAju.dto.external.CareerAdviceResponse;

import java.util.List;
import java.util.Map;

public record ConsultationResponse(
        // AI 커리어 조언
        List<CareerAdviceResponse.IndustryRecommendation> industries,
        List<String> interviewTips,
        List<String> strengths,
        String openaiModelVersion,

        // 관운 분석
        String favoredPeriod,
        int confidenceScore,
        String reasoning,

        // 사주 베이스 데이터 (Spring + OpenAI 혼합)
        SajuProfile sajuProfile,

        // OpenAI 분석 결과
        List<String> cautions,
        CareerAdviceResponse.WealthStyle wealthStyle,
        CareerAdviceResponse.LongTermRoadmap longTermRoadmap,
        CareerAdviceResponse.PersonalBranding personalBranding,
        CareerAdviceResponse.PowerKeywords powerKeywords,
        CareerAdviceResponse.MentalCare mentalCare,
        CareerAdviceResponse.EnvironmentFit environmentFit,
        CareerAdviceResponse.WorkStyle workStyle,
        CareerAdviceResponse.RelationshipStrategy relationshipStrategy,
        CareerAdviceResponse.CareerTimeline careerTimeline,

        // 분석에 사용된 사주 데이터 한 줄 요약
        String analysisSummary
) {
    public record SajuProfile(
            String dayMaster,
            String dayMasterDescription,
            Map<String, Integer> fiveElements,
            String fiveElementsAnalysis,
            Map<String, Integer> tenGodDistribution,
            List<String> keyTenGods
    ) {}
}
