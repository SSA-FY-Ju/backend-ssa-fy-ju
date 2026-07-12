package ssafy.SSAju.security.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.util.RedisKeyConstants;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

/**
 * Refresh Token을 Redis에 TTL과 함께 저장/조회/삭제하는 저장소.
 *
 * <p>키: {@code refresh-token:{jti}}, 값: {@code {"userId": .., "tokenHash": ..}} (JSON),
 * TTL: Refresh Token 만료 시간과 동일하게 설정하여 자연 만료 시 별도 삭제 없이도 조회 불가능해진다.
 */
@Repository
public class RefreshTokenRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RefreshTokenRedisRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(String jti, Long userId, String tokenHash, Duration ttl) {
        String value = objectMapper.writeValueAsString(new StoredRefreshToken(userId, tokenHash));
        redisTemplate.opsForValue().set(key(jti), value, ttl);
    }

    public Optional<StoredRefreshToken> find(String jti) {
        String value = redisTemplate.opsForValue().get(key(jti));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(value, StoredRefreshToken.class));
    }

    public void delete(String jti) {
        redisTemplate.delete(key(jti));
    }

    private String key(String jti) {
        return RedisKeyConstants.REFRESH_TOKEN_PREFIX + jti;
    }

    public record StoredRefreshToken(Long userId, String tokenHash) {}
}
