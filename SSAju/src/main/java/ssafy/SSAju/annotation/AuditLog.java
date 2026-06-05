package ssafy.SSAju.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 감사 로그 수집 대상 메서드를 지정하는 커스텀 어노테이션.
 *
 * <p>이 어노테이션이 부착된 메서드는 {@code AuditLoggingAspect}에 의해
 * 자동으로 감사 로그가 기록됩니다.</p>
 *
 * <pre>
 * {@code @AuditLog}
 * public void signup(SignupRequest request) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
}
