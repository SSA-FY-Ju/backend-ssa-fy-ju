package ssafy.SSAju.aspect;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.annotation.DistributedLock;
import ssafy.SSAju.exception.LockAcquisitionException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 분산락 Aspect 통합 테스트 (T009).
 * 동일 키에 대한 동시 요청의 상호 배제 및 대기 시간 초과 시 예외 발생을 검증합니다.
 */
@Testcontainers
@SpringBootTest
@DisplayName("분산락 Aspect 통합 테스트 (T009)")
class DistributedLockAspectTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("redisson.lock.wait-time-ms", () -> 500);
        registry.add("redisson.lock.lease-time-ms", () -> 3000);
    }

    @Autowired
    private LockedTestTarget lockedTestTarget;

    @Autowired
    private RedissonClient redissonClient;

    @AfterEach
    void tearDown() {
        redissonClient.getKeys().flushall();
    }

    @Test
    @DisplayName("T009-1: 동일 키에 대한 동시 요청은 순차적으로만 실행된다")
    void concurrentRequestsWithSameKey_executeSequentially() throws Exception {
        // Given
        String key = "concurrent-key";
        List<Instant[]> intervals = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        // When
        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                awaitQuietly(startLatch);
                intervals.add(lockedTestTarget.runAndRecordInterval(key, 300));
            });
        }
        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Then
        assertThat(intervals).hasSize(2);
        Instant[] first = intervals.get(0);
        Instant[] second = intervals.get(1);
        boolean nonOverlapping = !first[0].isBefore(second[1]) || !second[0].isBefore(first[1]);
        assertThat(nonOverlapping)
                .as("동일 키에 대한 두 실행 구간은 겹치지 않아야 한다")
                .isTrue();
    }

    @Test
    @DisplayName("T009-2: 락을 이미 점유 중이면 waitTime 초과 후 LockAcquisitionException이 발생한다")
    void lockAlreadyHeld_throwsLockAcquisitionExceptionAfterWaitTime() throws Exception {
        // Given: Redisson 락은 스레드 단위로 재진입 가능하므로, 같은 스레드에서 미리 lock()을 걸면
        // 아래 tryLock()이 대기 없이 재진입으로 성공해버린다. 다른 스레드가 점유하도록 해야 실제 경합을 재현한다.
        String key = "timeout-key";
        ExecutorService holderExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch lockHeldLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        holderExecutor.submit(() -> {
            var externalLock = redissonClient.getLock("lock:test:" + key);
            externalLock.lock();
            lockHeldLatch.countDown();
            awaitQuietly(releaseLatch);
            externalLock.unlock();
        });

        try {
            lockHeldLatch.await();

            // When & Then
            assertThatThrownBy(() -> lockedTestTarget.runAndRecordInterval(key, 100))
                    .isInstanceOf(LockAcquisitionException.class);
        } finally {
            releaseLatch.countDown();
            holderExecutor.shutdown();
            holderExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @TestConfiguration
    static class LockedTestTargetConfig {
        @Bean
        LockedTestTarget lockedTestTarget() {
            return new LockedTestTarget();
        }
    }

    static class LockedTestTarget {

        @DistributedLock(key = "'lock:test:' + #key")
        public Instant[] runAndRecordInterval(String key, long sleepMillis) {
            Instant start = Instant.now();
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new Instant[]{start, Instant.now()};
        }
    }
}
