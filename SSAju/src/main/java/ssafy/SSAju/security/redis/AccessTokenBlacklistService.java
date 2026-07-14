package ssafy.SSAju.security.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import ssafy.SSAju.util.RedisKeyConstants;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 로그아웃/탈퇴 시 Access Token을 즉시 무효화하기 위한 Redis 기반 블랙리스트.
 *
 * <p>키: {@code access-blacklist:{jti}}, TTL: 토큰의 남은 유효시간(+고정 패딩) —
 * 토큰이 자연 만료되면 블랙리스트 항목도 함께 만료되어 무한히 쌓이지 않는다.
 */
@Service
public class AccessTokenBlacklistService {

    private static final String BLACKLISTED_VALUE = "1";

    /**
     * 블랙리스트 TTL에 더하는 고정 패딩(초).
     *
     * <p>클럭 스큐(앱 서버-Redis 서버 간 시간 오차) 및 만료 시각 계산~SETEX 사이의
     * 지연을 방어하기 위해, 계산된 남은 유효시간에 이 값을 더해 저장한다.
     */
    private static final int BLACKLIST_TTL_PADDING_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public AccessTokenBlacklistService(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    public void blacklist(String jti, Instant tokenExpiry) {
        Duration remaining = Duration.between(clock.instant(), tokenExpiry);
        Duration ttl = (remaining.isNegative() ? Duration.ZERO : remaining)
                .plusSeconds(BLACKLIST_TTL_PADDING_SECONDS);
        redisTemplate.opsForValue().set(key(jti), BLACKLISTED_VALUE, ttl);
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(jti)));
    }

    private String key(String jti) {
        return RedisKeyConstants.ACCESS_BLACKLIST_PREFIX + jti;
    }
}
