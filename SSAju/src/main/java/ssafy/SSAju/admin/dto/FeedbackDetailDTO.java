package ssafy.SSAju.admin.dto;

import java.time.Instant;

public record FeedbackDetailDTO(
        Long feedbackId,
        Long userId,
        String feedbackType,
        String satisfactionStatus,
        String feedbackContent,
        Instant createdAt,
        String analysisType,
        Long analysisId,
        Instant analysisCreatedAt
) {}
