package ssafy.SSAju.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AnalysisDetailResponse(
        String type,
        Long analysisId,
        String targetName,
        LocalDate birthDate,
        LocalDateTime createdAt,
        String satisfactionStatus,
        String feedbackContent,
        CareerFortuneDetail careerFortuneDetail,
        ConsultationResponse consultationDetail,
        CompatibilityResponse compatibilityDetail
) {
    public record CareerFortuneDetail(
            String favoredPeriod,
            int confidenceScore,
            String reasoning
    ) {}
}
