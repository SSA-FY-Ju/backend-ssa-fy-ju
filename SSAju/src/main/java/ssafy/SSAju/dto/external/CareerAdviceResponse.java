package ssafy.SSAju.dto.external;

import java.util.List;
import java.util.Map;

public record CareerAdviceResponse(
        List<IndustryRecommendation> industries,
        List<String> interviewTips,
        List<String> strengths,
        List<String> cautions,
        WealthStyle wealthStyle,
        LongTermRoadmap longTermRoadmap,
        PersonalBranding personalBranding,
        PowerKeywords powerKeywords,
        MentalCare mentalCare,
        EnvironmentFit environmentFit,
        WorkStyle workStyle,
        RelationshipStrategy relationshipStrategy,
        CareerTimeline careerTimeline,
        List<String> keyTenGods,
        String dayMasterDescription,
        String fiveElementsAnalysis
) {
    public record IndustryRecommendation(
            String name,
            String reason,
            List<String> recommendedRoles
    ) {}

    public record WealthStyle(
            String incomeSource,
            String financialAdvice,
            String investmentTendency,
            String additionalIncome
    ) {}

    public record PhaseAdvice(
            String goal,
            String focus,
            String action
    ) {}

    public record LongTermRoadmap(
            PhaseAdvice phase0to2years,
            PhaseAdvice phase3to5years,
            String ultimateGoal,
            String goalDescription
    ) {}

    public record PersonalBranding(
            String suitColor,
            String impression,
            String hairAndMakeup,
            String brandingKeyword,
            String taglineForResume
    ) {}

    public record PowerKeyword(
            String keyword,
            String element,
            String description,
            String usageExample,
            String context
    ) {}

    public record PowerKeywords(
            List<PowerKeyword> keywords,
            String selectionGuide,
            List<String> usageTips,
            String avoidanceTip
    ) {}

    public record MentalCare(
            List<String> stressVulnerability,
            List<String> rechargeMethod,
            String mindsetMantra,
            String emergencyTactic
    ) {}

    public record EnvironmentFit(
            String workVibe,
            String companySize,
            String colleagueType,
            String conflictApproach,
            String physicalEnv,
            String culturalFit
    ) {}

    public record WorkStyle(
            String preferredCompanyType,
            String leadershipType,
            String decisionMaking,
            String conflictResolution
    ) {}

    public record RelationshipStrategy(
            String socialStyle,
            String networkingApproach,
            String teamPosition,
            String conflictResolution,
            String careerNetworking
    ) {}

    public record MonthFortune(
            String type,
            String description
    ) {}

    public record PivotPoint(
            String month,
            String type,
            int score,
            String description
    ) {}

    public record CareerTimeline(
            int year,
            Map<String, MonthFortune> months,
            List<PivotPoint> pivotPoints,
            List<String> warningMonths,
            String warningDescription
    ) {}
}
