package ssafy.SSAju.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 인증 이벤트 감사 로깅 Aspect.
 *
 * AuthService의 주요 메서드(회원가입, 로그인, 로그아웃, 토큰 갱신, 회원 탈퇴)에 대한
 * 감사 로그를 중앙화하여 기록합니다.
 *
 * 로그 형식: [AUDIT] action=?, userId=?, status=SUCCESS|FAILURE, durationMs=?
 * - userId: 메서드 첫 번째 인자가 Long인 경우 추출 (logout, deleteUser)
 *           그 외(signup, login, refresh)는 null로 기록
 * - 개인정보(email, password 등)는 로그에 포함하지 않음
 */
@Slf4j
@Aspect
@Component
public class AuditLoggingAspect {

    @Around("execution(* ssafy.SSAju.service.AuthService.signup(..)) || " +
            "execution(* ssafy.SSAju.service.AuthService.login(..)) || " +
            "execution(* ssafy.SSAju.service.AuthService.logout(..)) || " +
            "execution(* ssafy.SSAju.service.AuthService.refreshAccessToken(..)) || " +
            "execution(* ssafy.SSAju.service.AuthService.deleteUser(..))")
    public Object logAuthEvent(ProceedingJoinPoint jp) throws Throwable {
        String action = jp.getSignature().getName();
        Instant start = Instant.now();

        Long userId = extractUserId(jp.getArgs());

        try {
            Object result = jp.proceed();
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.info("[AUDIT] action={}, userId={}, status=SUCCESS, durationMs={}",
                    action, userId, durationMs);
            return result;
        } catch (Exception e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.warn("[AUDIT] action={}, userId={}, status=FAILURE, durationMs={}, reason={}",
                    action, userId, durationMs, e.getClass().getSimpleName());
            throw e;
        }
    }

    private Long extractUserId(Object[] args) {
        if (args != null && args.length > 0 && args[0] instanceof Long id) {
            return id;
        }
        return null;
    }
}
