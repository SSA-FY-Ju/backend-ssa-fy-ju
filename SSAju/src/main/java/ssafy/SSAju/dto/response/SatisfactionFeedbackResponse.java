package ssafy.SSAju.dto.response;

import java.time.LocalDateTime;

public record SatisfactionFeedbackResponse(
        Long feedbackId,
        LocalDateTime createdAt,
        String feedbackContent
) {
}
