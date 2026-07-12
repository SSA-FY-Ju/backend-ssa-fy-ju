package ssafy.SSAju.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import ssafy.SSAju.annotation.DistributedLock;
import ssafy.SSAju.config.RedissonLockProperties;
import ssafy.SSAju.exception.LockAcquisitionException;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * {@code @DistributedLock} 어노테이션이 부착된 메서드를 Redisson {@link RLock}으로 감싸는 Aspect.
 *
 * <p>{@code key}의 SpEL 표현식을 메서드 파라미터로 평가해 락 키를 만들고,
 * {@link RedissonLockProperties}의 wait/lease time으로 {@code tryLock}을 시도합니다.
 * 락 획득에 실패하면 {@link LockAcquisitionException}을 던져 재시도를 유도합니다.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)}로 트랜잭션 어드바이저(기본 {@code LOWEST_PRECEDENCE})보다
 * 가장 바깥쪽에 위치시킵니다. 락이 트랜잭션 시작 전에 획득되고 트랜잭션 커밋/롤백이 끝난 뒤
 * 해제되어야 동일 키에 대한 동시 요청 간 데이터 정합성이 보장되기 때문입니다.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DistributedLockAspect {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final ConcurrentHashMap<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>();

    private final RedissonClient redissonClient;
    private final RedissonLockProperties lockProperties;

    @Around("@annotation(ssafy.SSAju.annotation.DistributedLock)")
    public Object lock(ProceedingJoinPoint jp) throws Throwable {
        Method method = ((MethodSignature) jp.getSignature()).getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        String lockKey = evaluateKey(method, jp.getArgs(), distributedLock.key());
        RLock rLock = redissonClient.getLock(lockKey);

        boolean acquired;
        try {
            acquired = rLock.tryLock(
                    lockProperties.getWaitTimeMs(), lockProperties.getLeaseTimeMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("락 대기 중 스레드가 중단되었습니다.", e);
        }

        if (!acquired) {
            // 락 키는 SpEL로 계산되어 생년월일 등 개인정보를 포함할 수 있으므로
            // 로그/예외 메시지에 원문을 남기지 않고 메서드명만 기록한다.
            log.warn("분산락 획득 실패: method={}", method.getName());
            throw new LockAcquisitionException("락 획득에 실패했습니다.");
        }

        try {
            return jp.proceed();
        } finally {
            if (rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }
    }

    private String evaluateKey(Method method, Object[] args, String keyExpression) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] parameterNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        Expression expression = EXPRESSION_CACHE.computeIfAbsent(keyExpression, PARSER::parseExpression);
        String lockKey = expression.getValue(context, String.class);
        if (lockKey == null) {
            throw new LockAcquisitionException(
                    "락 키 계산 결과가 null입니다. @DistributedLock SpEL 표현식을 확인하세요: method=" + method.getName());
        }
        return lockKey;
    }
}
