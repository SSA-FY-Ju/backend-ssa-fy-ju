package ssafy.SSAju.event;

import ssafy.SSAju.entity.enums.LoginFailureReason;

import java.time.LocalDateTime;

public record LoginAttemptEvent(
        String email,
        boolean success,
        LoginFailureReason failureReason,
        String ipAddress,
        LocalDateTime attemptedAt
) {}
