package ssafy.SSAju.dto.response;

import java.time.Instant;

public record SatisfactionFeedbackResponse(
        Long feedbackId,
        Instant createdAt,
        String feedbackContent
) {
}
