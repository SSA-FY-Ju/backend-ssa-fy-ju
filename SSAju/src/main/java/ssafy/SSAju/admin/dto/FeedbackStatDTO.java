package ssafy.SSAju.admin.dto;

import java.util.Map;

public record FeedbackStatDTO(
        Map<String, Long> satisfiedCountByFeedbackType,
        Map<String, Long> unsatisfiedCountByFeedbackType,
        long totalFeedbackCount
) {}
