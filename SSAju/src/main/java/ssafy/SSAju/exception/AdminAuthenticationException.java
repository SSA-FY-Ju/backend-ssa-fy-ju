package ssafy.SSAju.exception;

import ssafy.SSAju.admin.enums.AdminLoginFailureReason;

public class AdminAuthenticationException extends AuthException {

    private final AdminLoginFailureReason reason;

    public AdminAuthenticationException(String message, AdminLoginFailureReason reason) {
        super(message);
        this.reason = reason;
    }

    public AdminLoginFailureReason getReason() {
        return reason;
    }
}
