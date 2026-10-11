package ssafy.SSAju.aspect;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ssafy.SSAju.admin.service.AdminAuditLogUtil;
import ssafy.SSAju.exception.AdminAuthenticationException;

@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class AdminAuthenticationAuditAspect {

    private final AdminAuditLogUtil auditLog;

    @Around(value = "@annotation(ssafy.SSAju.annotation.AuditLog)"
            + " && execution(* ssafy.SSAju.admin.service.AdminAuthenticationService.validateAdminCredentials(..))"
            + " && args(email,..)", argNames = "jp,email")
    public Object logAdminAuthentication(ProceedingJoinPoint jp, String email) throws Throwable {
        Long adminId;
        try {
            adminId = (Long) jp.proceed();
        } catch (AdminAuthenticationException e) {
            auditLog.loginFailed(email, e.getReason());
            throw e;
        }
        auditLog.loginSucceeded(adminId);
        return adminId;
    }
}
