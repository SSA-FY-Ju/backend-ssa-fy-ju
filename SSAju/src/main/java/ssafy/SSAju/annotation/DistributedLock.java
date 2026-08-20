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
 * 파라미터명을 {@code root}로 짓지 마세요 — SpEL에서 {@code #root}는 평가 루트 컨텍스트를
 * 가리키는 예약 변수라 동명의 파라미터를 바인딩해도 가려져서 항상 null로 평가됩니다.
 *
 * <pre>
 * {@code @DistributedLock(key = "'lock:saju-result:' + #userProfileId")}
 * public SajuResult findOrCreate(Long userProfileId) { ... }
 * </pre>
 *
 * <p>{@code DistributedLockAspect}가 이 어노테이션이 부착된 메서드 실행 전후로
 * {@code RLock.tryLock()}/{@code unlock()}을 수행합니다.
 *
 * <p><strong>⚠️ self-invocation 주의 (필수 확인)</strong>: Spring AOP는 프록시 기반이라,
 * 같은 클래스 안에서 {@code this.someLockedMethod()}처럼 호출하면 프록시를 거치지 않아
 * 이 어노테이션이 조용히 무시됩니다(예외 없이 락 없는 채로 그냥 실행됨). 이 어노테이션을
 * 붙인 메서드를 호출하는 다른 코드가 같은 클래스 안에 있다면 반드시 별도 빈으로 분리하세요
 * ({@code CompanyMatchingService}가 락 없는 1차 캐시 조회와 락이 필요한 저장 단계를
 * {@code CompanyCompatibilitySaveService}로 분리한 것, {@code ConsultationSaveService}가
 * 신규 삽입을 {@code ConsultationInsertService}로 분리한 것 참고). 이 아스펙트 자체는
 * self-invocation을 감지할 방법이 없습니다 — 애초에 아스펙트가 호출되지 않기 때문입니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 락 키를 계산하는 SpEL 표현식. 메서드 파라미터는 {@code #paramName}으로 참조합니다.
     */
    String key();
}
