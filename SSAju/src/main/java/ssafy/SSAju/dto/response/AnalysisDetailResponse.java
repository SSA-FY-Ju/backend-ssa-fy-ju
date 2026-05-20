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
        CompanyCompatibilityDetail companyCompatibilityDetail
) {
    public record CareerFortuneDetail(
            String favoredPeriod,
            int confidenceScore,
            String reasoning
    ) {}

    public record CompanyCompatibilityDetail(
            String companyName,
            int compatibilityScore,
            String summary
    ) {}
}
