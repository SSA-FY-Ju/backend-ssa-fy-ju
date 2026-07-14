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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
    void save_afterTtlExpires_findReturnsEmpty() {
        // Given
        String jti = "ttl-jti-" + System.nanoTime();
        refreshTokenRedisRepository.save(jti, 1L, "hash", Duration.ofSeconds(1));
        assertThat(refreshTokenRedisRepository.find(jti)).isPresent();

        // When & Then: 자연 만료 대기 (별도 delete 호출 없음) — 만료 즉시 폴링을 종료해 불필요한 대기를 없앤다
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(refreshTokenRedisRepository.find(jti)).isEmpty());
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

    @Test
    @DisplayName("T012-5: consume은 해시가 일치하면 값을 반환하고 즉시 삭제한다")
    void consume_matchingHash_returnsValueAndDeletes() {
        // Given
        String jti = "consume-jti-" + System.nanoTime();
        refreshTokenRedisRepository.save(jti, 5L, "correct-hash", Duration.ofMinutes(5));

        // When
        Optional<RefreshTokenRedisRepository.StoredRefreshToken> consumed =
                refreshTokenRedisRepository.consume(jti, "correct-hash");

        // Then
        assertThat(consumed).isPresent();
        assertThat(consumed.get().userId()).isEqualTo(5L);
        assertThat(refreshTokenRedisRepository.find(jti)).isEmpty();
    }

    @Test
    @DisplayName("T012-6: consume은 해시가 불일치해도 항목을 삭제하고 빈 Optional을 반환한다 (재사용 방지)")
    void consume_mismatchingHash_returnsEmptyAndStillDeletes() {
        // Given
        String jti = "consume-mismatch-jti-" + System.nanoTime();
        refreshTokenRedisRepository.save(jti, 5L, "correct-hash", Duration.ofMinutes(5));

        // When
        Optional<RefreshTokenRedisRepository.StoredRefreshToken> consumed =
                refreshTokenRedisRepository.consume(jti, "wrong-hash");

        // Then
        assertThat(consumed).isEmpty();
        assertThat(refreshTokenRedisRepository.find(jti)).isEmpty();
    }

    @Test
    @DisplayName("T012-7: 존재하지 않는 jti를 consume하면 빈 Optional 반환")
    void consume_unknownJti_returnsEmpty() {
        assertThat(refreshTokenRedisRepository.consume("never-saved-jti", "any-hash")).isEmpty();
    }

    @Test
    @DisplayName("T012-8: 동일한 RefreshToken으로 동시에 갱신 요청이 들어와도 consume은 정확히 하나만 성공한다 (원자적 GETDEL)")
    void consume_concurrentCallsWithSameToken_onlyOneSucceeds() throws Exception {
        // Given
        String jti = "concurrent-jti-" + System.nanoTime();
        String hash = "shared-hash";
        refreshTokenRedisRepository.save(jti, 9L, hash, Duration.ofMinutes(5));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        // When: 모든 스레드가 동시에 같은 토큰으로 consume 시도
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return refreshTokenRedisRepository.consume(jti, hash).isPresent();
            }));
        }
        ready.await();
        start.countDown();

        long successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successCount++;
            }
        }
        executor.shutdown();

        // Then: 정확히 하나의 요청만 값을 가져가고, 이후 재조회는 불가능하다
        assertThat(successCount).isEqualTo(1);
        assertThat(refreshTokenRedisRepository.find(jti)).isEmpty();
    }
}
