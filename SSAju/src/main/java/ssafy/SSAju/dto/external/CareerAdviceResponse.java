package ssafy.SSAju.dto.external;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    ) {
        /** null 요소가 제거된 keywords 목록을 반환합니다. */
        public List<PowerKeyword> validKeywords() {
            if (keywords == null) return List.of();
            return keywords.stream().filter(k -> k != null).toList();
        }

        /** null 요소가 제거된 usageTips 목록을 반환합니다. */
        public List<String> validUsageTips() {
            if (usageTips == null) return List.of();
            return usageTips.stream().filter(t -> t != null).toList();
        }
    }

    public record MentalCare(
            List<String> stressVulnerability,
            List<String> rechargeMethod,
            String mindsetMantra,
            String emergencyTactic
    ) {
        /** null 요소가 제거된 stressVulnerability 목록을 반환합니다. */
        public List<String> validStressVulnerability() {
            if (stressVulnerability == null) return List.of();
            return stressVulnerability.stream().filter(s -> s != null).toList();
        }

        /** null 요소가 제거된 rechargeMethod 목록을 반환합니다. */
        public List<String> validRechargeMethod() {
            if (rechargeMethod == null) return List.of();
            return rechargeMethod.stream().filter(r -> r != null).toList();
        }
    }

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
    ) {
        /** null value가 포함된 month 항목을 제거한 안전한 맵을 반환합니다. */
        public Map<String, MonthFortune> validMonths() {
            if (months == null) return Map.of();
            return months.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        /** null 요소가 제거된 pivotPoints 목록을 반환합니다. */
        public List<PivotPoint> validPivotPoints() {
            if (pivotPoints == null) return List.of();
            return pivotPoints.stream().filter(pp -> pp != null).toList();
        }

        /** null 요소가 제거된 warningMonths 목록을 반환합니다. */
        public List<String> validWarningMonths() {
            if (warningMonths == null) return List.of();
            return warningMonths.stream().filter(m -> m != null).toList();
        }
    }
}
