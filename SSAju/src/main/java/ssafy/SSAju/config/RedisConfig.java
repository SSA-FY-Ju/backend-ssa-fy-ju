package ssafy.SSAju.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 연결은 {@code spring-boot-starter-data-redis}가 {@code spring.data.redis.host/port}로
 * {@link RedisConnectionFactory}를 자동 구성하므로 별도 등록하지 않는다.
 *
 * <p>Refresh Token / Access Token 블랙리스트는 모두 문자열 값(JTI, 토큰 해시)이므로
 * 기본 JDK 직렬화 대신 {@link StringRedisTemplate}을 명시적으로 등록해 Redis CLI로
 * 값을 직접 조회/디버깅할 수 있게 한다.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }
}
