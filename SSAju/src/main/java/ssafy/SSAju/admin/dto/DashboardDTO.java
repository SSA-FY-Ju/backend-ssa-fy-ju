package ssafy.SSAju.admin.dto;

import java.util.Map;

public record DashboardDTO(
        long totalAnalysis,
        Map<String, Long> analysisTypeBreakdown,
        long dailyLimitExhaustedCount,
        FeedbackSummary feedbackSummary
) {
    public record FeedbackSummary(
            long satisfiedCount,
            long unsatisfiedCount,
            long totalCount
    ) {}
}
