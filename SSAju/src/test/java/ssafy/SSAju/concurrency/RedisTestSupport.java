package ssafy.SSAju.concurrency;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;

/**
 * 분산락 동시성 테스트가 공통으로 필요로 하는 Redis Testcontainers 설정을 모아둔 헬퍼입니다.
 *
 * <p>공용 추상 테스트 베이스 클래스(정적 필드로 컨테이너 하나를 공유)가 아니라 순수 유틸리티
 * 메서드로 만들었습니다 — 각 테스트 클래스가 독립된 Redis 컨테이너/Spring 컨텍스트를
 * 가져야 합니다. 컨테이너를 공유하면 동시성 테스트 여러 개가 같은 H2 DB/Redisson 커넥션
 * 풀을 나눠 써서, 한 테스트 클래스가 남긴 데이터나 아직 정리 중인 스레드가 다른 클래스의
 * 실행과 부딪히는 문제가 실제로 발생했습니다(예: A 클래스가 만든 자식 엔티티가 남아있는 채로
 * B 클래스가 부모를 지우려다 FK 위반). 반복되는 건 "컨테이너를 어떻게 만들고 프로퍼티를
 * 어떻게 등록하는가"라는 코드일 뿐, 컨테이너 인스턴스 자체를 공유할 필요는 없다.
 */
final class RedisTestSupport {

    private RedisTestSupport() {}

    static GenericContainer<?> newRedisContainer() {
        return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    }

    static void registerRedisProperties(DynamicPropertyRegistry registry, GenericContainer<?> redis) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
