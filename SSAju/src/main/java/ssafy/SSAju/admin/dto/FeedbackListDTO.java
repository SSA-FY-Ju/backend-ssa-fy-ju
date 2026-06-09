package ssafy.SSAju.admin.dto;

import ssafy.SSAju.career.enums.FeedbackType;
import ssafy.SSAju.career.enums.SatisfactionStatus;

import java.time.Instant;

public record FeedbackListDTO(
        Long id,
        Long userId,
        String feedbackContent,
        SatisfactionStatus satisfactionStatus,
        FeedbackType feedbackType,
        Instant createdAt
) {}
