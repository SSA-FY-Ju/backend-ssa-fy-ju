package ssafy.SSAju.security.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RefreshTokenRedisRepository 통합 테스트 (T012).
 * TTL 만료 후 자동으로 조회 불가능해지는지 검증합니다.
 */
@Testcontainers
@SpringBootTest
@DisplayName("RefreshTokenRedisRepository 통합 테스트 (T012)")
class RefreshTokenRedisRepositoryTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RefreshTokenRedisRepository refreshTokenRedisRepository;

    @Test
    @DisplayName("T012-1: 저장한 RefreshToken을 jti로 다시 조회할 수 있다")
    void save_thenFind_returnsStoredValue() {
        // Given
        String jti = "jti-" + System.nanoTime();

        // When
        refreshTokenRedisRepository.save(jti, 42L, "hashed-token-value", Duration.ofMinutes(5));
        Optional<RefreshTokenRedisRepository.StoredRefreshToken> found = refreshTokenRedisRepository.find(jti);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().userId()).isEqualTo(42L);
        assertThat(found.get().tokenHash()).isEqualTo("hashed-token-value");
    }

    @Test
    @DisplayName("T012-2: TTL이 만료되면 별도 삭제 없이도 조회 불가능해진다")
    void save_afterTtlExpires_findReturnsEmpty() throws InterruptedException {
        // Given
        String jti = "ttl-jti-" + System.nanoTime();
        refreshTokenRedisRepository.save(jti, 1L, "hash", Duration.ofSeconds(1));
        assertThat(refreshTokenRedisRepository.find(jti)).isPresent();

        // When: 자연 만료 대기 (별도 delete 호출 없음)
        Thread.sleep(1500);

        // Then
        assertThat(refreshTokenRedisRepository.find(jti)).isEmpty();
    }

    @Test
    @DisplayName("T012-3: delete 호출 시 즉시 조회 불가능해진다 (로그아웃/회전)")
    void delete_removesEntryImmediately() {
        // Given
        String jti = "delete-jti-" + System.nanoTime();
        refreshTokenRedisRepository.save(jti, 7L, "hash", Duration.ofMinutes(5));
        assertThat(refreshTokenRedisRepository.find(jti)).isPresent();

        // When
        refreshTokenRedisRepository.delete(jti);

        // Then
        assertThat(refreshTokenRedisRepository.find(jti)).isEmpty();
    }

    @Test
    @DisplayName("T012-4: 존재하지 않는 jti 조회 시 빈 Optional 반환")
    void find_unknownJti_returnsEmpty() {
        assertThat(refreshTokenRedisRepository.find("never-saved-jti")).isEmpty();
    }
}
