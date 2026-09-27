package ssafy.SSAju.career.dto.response;

import java.time.Instant;

public record SatisfactionFeedbackResponse(
        Long feedbackId,
        Instant createdAt,
        String feedbackContent
) {
}
