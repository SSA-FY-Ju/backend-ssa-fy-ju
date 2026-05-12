package ssafy.SSAju.dto.response;

import ssafy.SSAju.career.enums.ForecastStatus;
import ssafy.SSAju.career.util.JobCategoryEnum;

import java.util.List;

public record CompatibilityResponse(
        RequestContext requestContext,
        Integer compatibilityScore,
        String summary,
        TargetRoleAnalysis targetRoleAnalysis,
        FiveElements fiveElements,
        AnalysisBreakdown analysisBreakdown,
        ActionableStrategy actionableStrategy,
        List<InterviewQuestion> expectedInterviewQuestions,
        List<RoleCompatibility> roleCompatibility,
        List<MonthlyForecast> monthlyForecast,
        List<String> cautions
) {

    public record RequestContext(
            String companyName,
            TargetRoleInfo targetRole
    ) {}

    public record TargetRoleInfo(
            JobCategoryEnum category,
            String detailName
    ) {}

    public record TargetRoleAnalysis(
            Integer matchScore,
            String synergy,
            String warning
    ) {}

    public record FiveElements(
            java.util.Map<String, Integer> userDistribution,
            java.util.Map<String, Integer> companyDistribution,
            String synergyDescription
    ) {}

    public record AnalysisBreakdown(
            Integer characterMatch,
            Integer potentialSynergy,
            Integer longTermStability
    ) {}

    public record ActionableStrategy(
            List<String> interviewKeywords,
            String weaknessDefense,
            BestTiming bestTiming
    ) {
        public record BestTiming(
                List<String> luckyDays,
                String preferredTime
        ) {}
    }

    public record InterviewQuestion(
            String question,
            String intent
    ) {}

    public record RoleCompatibility(
            String roleName,
            Integer score,
            String reason,
            String tag
    ) {}

    public record MonthlyForecast(
            Integer month,
            Integer score,
            ForecastStatus status,
            String advice
    ) {}
}
