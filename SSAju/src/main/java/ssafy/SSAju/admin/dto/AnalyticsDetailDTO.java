package ssafy.SSAju.admin.dto;

import java.time.Instant;

public record AnalyticsDetailDTO(
        Long id,
        Long userId,
        String analysisType,
        String jsonData,
        Instant createdAt
) {}
