package ssafy.SSAju.dto.response;

import java.time.Instant;
import java.time.LocalDate;

public record UserAnalysisDto(
        String type,
        Long analysisId,
        String targetName,
        LocalDate birthDate,
        Instant createdAt
) {}
