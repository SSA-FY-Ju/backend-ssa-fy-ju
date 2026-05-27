package ssafy.SSAju.dto.response;

import java.time.Instant;
import java.time.LocalDate;

public record AnalysisDetailResponse(
        String type,
        Long analysisId,
        String targetName,
        LocalDate birthDate,
        Instant createdAt,
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
