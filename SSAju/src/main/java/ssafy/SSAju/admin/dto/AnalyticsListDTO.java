package ssafy.SSAju.admin.dto;

import java.time.Instant;

public record AnalyticsListDTO(
        Long id,
        Long userId,
        String analysisType,
        Instant createdAt
) {}
