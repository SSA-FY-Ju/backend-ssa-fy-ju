package ssafy.SSAju.admin.dto;

import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.SatisfactionStatus;

import java.time.Instant;

public record FeedbackListDTO(
        Long id,
        Long userId,
        String feedbackContent,
        SatisfactionStatus satisfactionStatus,
        AnalysisType feedbackType,
        Instant createdAt
) {}
