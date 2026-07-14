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
        return Optional.ofNullable(value).map(v -> objectMapper.readValue(v, StoredRefreshToken.class));
    }

    /**
     * Refresh Token을 원자적으로 조회하며 즉시 삭제(1회용 소비)한다.
     *
     * <p>Redis {@code GETDEL} 명령으로 조회와 삭제를 하나의 원자적 연산으로 수행하므로,
     * 동일한 토큰으로 동시에 여러 갱신 요청이 들어와도 정확히 한 요청만 값을 가져가고
     * 나머지는 빈 값을 받는다({@code find} → 해시 검증 → {@code delete}로 분리했을 때 발생하는
     * TOCTOU 경쟁 조건을 제거). 해시가 일치하지 않아도(위조/재사용 시도) 항목은 이미
     * 삭제된 상태이므로 이후 정상 토큰으로도 재사용할 수 없다.
     *
     * @param jti          Refresh Token의 jti
     * @param expectedHash 요청으로 들어온 Refresh Token 원본값의 SHA-256 해시
     * @return 저장된 값이 존재하고 해시가 일치하면 그 값, 아니면 빈 Optional
     */
    public Optional<StoredRefreshToken> consume(String jti, String expectedHash) {
        String value = redisTemplate.opsForValue().getAndDelete(key(jti));
        if (value == null) {
            return Optional.empty();
        }
        StoredRefreshToken stored = objectMapper.readValue(value, StoredRefreshToken.class);
        return expectedHash.equals(stored.tokenHash()) ? Optional.of(stored) : Optional.empty();
    }

    public void delete(String jti) {
        redisTemplate.delete(key(jti));
    }

    private String key(String jti) {
        return RedisKeyConstants.REFRESH_TOKEN_PREFIX + jti;
    }

    public record StoredRefreshToken(Long userId, String tokenHash) {}
}
