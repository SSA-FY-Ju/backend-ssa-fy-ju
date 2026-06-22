package ssafy.SSAju.admin.dto;

public record UsageAdjustmentResponseDTO(
        Long userId,
        String usageDate,
        int usageCountBefore,
        int usageCountAfter,
        String action
) {}
