package ssafy.SSAju.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redisson 분산락으로 보호할 메서드에 부착하는 어노테이션.
 *
 * <p>{@code key}는 메서드 파라미터를 참조하는 SpEL 표현식입니다. 락 키 prefix는
 * {@link ssafy.SSAju.util.RedisKeyConstants}의 상수를 문자열 리터럴로 조합해 사용합니다.
 *
 * <pre>
 * {@code @DistributedLock(key = "'lock:saju-result:' + #userProfileId")}
 * public SajuResult findOrCreate(Long userProfileId) { ... }
 * </pre>
 *
 * <p>{@code DistributedLockAspect}가 이 어노테이션이 부착된 메서드 실행 전후로
 * {@code RLock.tryLock()}/{@code unlock()}을 수행합니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 락 키를 계산하는 SpEL 표현식. 메서드 파라미터는 {@code #paramName}으로 참조합니다.
     */
    String key();
}
