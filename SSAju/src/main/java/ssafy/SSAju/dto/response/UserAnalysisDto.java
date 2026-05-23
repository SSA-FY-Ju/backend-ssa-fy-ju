package ssafy.SSAju.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserAnalysisDto(
        String type,
        Long analysisId,
        String targetName,
        LocalDate birthDate,
        LocalDateTime createdAt
) {}
