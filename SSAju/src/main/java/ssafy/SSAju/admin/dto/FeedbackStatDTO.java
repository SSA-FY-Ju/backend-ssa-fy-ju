package ssafy.SSAju.admin.dto;

import java.util.Map;

public record FeedbackStatDTO(
        Map<String, Long> satisfiedCountByFeedbackType,
        Map<String, Long> unsatisfiedCountByFeedbackType,
        long totalFeedbackCount
) {
    public long totalSatisfied() {
        return satisfiedCountByFeedbackType.values().stream().mapToLong(Long::longValue).sum();
    }

    public long totalUnsatisfied() {
        return unsatisfiedCountByFeedbackType.values().stream().mapToLong(Long::longValue).sum();
    }
}
