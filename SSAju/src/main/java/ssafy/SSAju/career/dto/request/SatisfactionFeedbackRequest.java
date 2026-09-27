package ssafy.SSAju.career.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.SatisfactionStatus;

public record SatisfactionFeedbackRequest(
        @NotNull(message = "analysisId는 필수입니다")
        Long analysisId,

        @NotNull(message = "feedbackType은 필수입니다")
        AnalysisType feedbackType,

        @NotNull(message = "satisfactionStatus는 필수입니다")
        SatisfactionStatus satisfactionStatus,

        @Size(max = 500, message = "feedbackContent는 최대 500자입니다")
        String feedbackContent
) {
}
