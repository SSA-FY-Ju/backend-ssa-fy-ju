package ssafy.SSAju.career.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ssafy.SSAju.career.enums.SatisfactionStatus;

/**
 * 만족도 피드백 작성 요청.
 *
 * <p>분석 유형(feedbackType)을 받지 않는다 — 허용 여부와 표시 유형은 모두 연결된 분석의
 * {@code analysis_type}에서 판정하므로, 클라이언트가 보낸 유형과 실제 분석이 어긋날 여지를 없앤다.
 */
public record SatisfactionFeedbackRequest(
        @NotNull(message = "analysisId는 필수입니다")
        Long analysisId,

        @NotNull(message = "satisfactionStatus는 필수입니다")
        SatisfactionStatus satisfactionStatus,

        @Size(max = 500, message = "feedbackContent는 최대 500자입니다")
        String feedbackContent
) {
}
