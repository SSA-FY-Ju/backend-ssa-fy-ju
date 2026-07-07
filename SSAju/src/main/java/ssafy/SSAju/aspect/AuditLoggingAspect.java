package ssafy.SSAju.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import ssafy.SSAju.annotation.AuditableRequest;

import java.time.Duration;
import java.time.Instant;

/**
 * 감사 로그 수집 Aspect.
 *
 * {@code @AuditLog} 어노테이션이 부착된 메서드에 대해 자동으로 감사 로그를 기록합니다.
 * 새로운 메서드 추가 시 해당 메서드에 {@code @AuditLog}만 붙이면 되므로
 * 이 Aspect를 직접 수정할 필요가 없습니다.
 *
 * <p>로그 형식: [AUDIT] action=?, userId=?, status=SUCCESS|FAILURE, durationMs=?</p>
 * <ul>
 *   <li>userId: 첫 파라미터가 Long이면 추출 (logout, deleteUser),
 *               AuditableRequest 구현체면 {@code getAuditUserId()} 호출</li>
 *   <li>개인정보(email, password 등)는 로그에 포함하지 않음</li>
 * </ul>
 *
 * <p>{@code @Order(LOWEST_PRECEDENCE - 1)}로 트랜잭션 어드바이저(기본 {@code LOWEST_PRECEDENCE})보다
 * 바깥쪽에 위치시킵니다. 이 어드바이스가 감싸는 트랜잭션 커밋까지 끝난 뒤 감사 로그가 기록되도록
 * 순서를 고정하기 위함이며, 순서를 지정하지 않으면 두 어드바이스 모두 동일한 기본 우선순위를 가져
 * 어느 쪽이 바깥쪽인지 보장되지 않습니다.</p>
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class AuditLoggingAspect {

    @Around("@annotation(ssafy.SSAju.annotation.AuditLog)")
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

    /**
     * 메서드 인자에서 감사 로그용 userId를 추출합니다.
     *
     * <p>추출 우선순위:
     * <ol>
     *   <li>{@link AuditableRequest} 구현체 → {@code getAuditUserId()} 호출</li>
     *   <li>타입이 {@code Long}인 인자 (logout, deleteUser의 userId 파라미터)</li>
     * </ol>
     * 파라미터 순서에 의존하지 않으므로 메서드 시그니처 변경에 안전합니다.
     */
    private Long extractUserId(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof AuditableRequest request) {
                return request.getAuditUserId();
            }
            if (arg instanceof Long id) {
                return id;
            }
        }
        return null;
    }
}
