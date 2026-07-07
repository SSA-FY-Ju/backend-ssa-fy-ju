package ssafy.SSAju.admin.dto;

public record UsageAdjustedEvent(Long userId, AdjustmentAction action, int before, int after) {}
