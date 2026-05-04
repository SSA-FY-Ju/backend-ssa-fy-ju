package ssafy.SSAju.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ssafy.SSAju.career.enums.FeedbackType;
import ssafy.SSAju.career.enums.SatisfactionStatus;

public record SatisfactionFeedbackRequest(
        @NotNull(message = "sajuResultId는 필수입니다")
        Long sajuResultId,

        @NotNull(message = "feedbackType은 필수입니다")
        FeedbackType feedbackType,

        @NotNull(message = "satisfactionStatus는 필수입니다")
        SatisfactionStatus satisfactionStatus,

        @Size(max = 500, message = "feedbackContent는 최대 500자입니다")
        String feedbackContent
) {
}
