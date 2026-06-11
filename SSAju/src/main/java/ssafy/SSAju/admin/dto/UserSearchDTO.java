package ssafy.SSAju.admin.dto;

import ssafy.SSAju.entity.enums.UserStatus;

import java.time.Instant;

public record UserSearchDTO(
        Long id,
        String email,
        String name,
        Instant joinDate,
        UserStatus status,
        Instant deletedAt,
        long totalAnalysisCount
) {}
