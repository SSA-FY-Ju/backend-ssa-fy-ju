package ssafy.SSAju.integration;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.DailyLimitExceededException;
import ssafy.SSAju.repository.DailyApiUsageRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.service.DailyApiUsageService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 일일 API 요청 제한 통합 테스트 (T045).
 * MySQL ON DUPLICATE KEY UPDATE 문법을 사용하므로 Testcontainers 필수.
 */
@Slf4j
@Testcontainers
@SpringBootTest
@DisplayName("일일 API 요청 제한 통합 테스트 (T045)")
class DailyApiUsageIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired
    private DailyApiUsageService dailyApiUsageService;

    @Autowired
    private DailyApiUsageRepository dailyApiUsageRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        dailyApiUsageRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("daily-test@test.com")
                .passwordHash("hash")
                .name("일일제한테스터")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(Instant.now())
                .privacyAgreedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("T045-1: 하루 3회 요청 모두 성공")
    void threeRequests_allSucceed() {
        dailyApiUsageService.checkAndIncrementDailyUsage(testUser.getId());
        dailyApiUsageService.checkAndIncrementDailyUsage(testUser.getId());
        dailyApiUsageService.checkAndIncrementDailyUsage(testUser.getId());

        assertThat(dailyApiUsageRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("T045-2: 4번째 요청 → DailyLimitExceededException (429)")
    void fourthRequest_throwsDailyLimitExceededException() {
        dailyApiUsageService.checkAndIncrementDailyUsage(testUser.getId());
        dailyApiUsageService.checkAndIncrementDailyUsage(testUser.getId());
        dailyApiUsageService.checkAndIncrementDailyUsage(testUser.getId());

        assertThatThrownBy(() -> dailyApiUsageService.checkAndIncrementDailyUsage(testUser.getId()))
                .isInstanceOf(DailyLimitExceededException.class);
    }

    @Test
    @DisplayName("T045-3: 동시 10개 요청 → 정확히 3개만 성공 (Race Condition 안전)")
    void concurrentRequests_exactlyThreeSucceed() throws InterruptedException {
        int totalThreads = 10;
        int expectedSuccess = 3;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Exception> unexpectedErrors = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    dailyApiUsageService.checkAndIncrementDailyUsage(testUser.getId());
                    successCount.incrementAndGet();
                } catch (DailyLimitExceededException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        log.info("동시 요청 결과: success={}, fail={}", successCount.get(), failCount.get());
        assertThat(unexpectedErrors).isEmpty();
        assertThat(successCount.get()).isEqualTo(expectedSuccess);
        assertThat(failCount.get()).isEqualTo(totalThreads - expectedSuccess);
    }
}
