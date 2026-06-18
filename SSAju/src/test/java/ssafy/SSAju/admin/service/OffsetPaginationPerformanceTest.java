package ssafy.SSAju.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.admin.dto.UserSearchDTO;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.repository.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Testcontainers
@SpringBootTest
@DisplayName("OFFSET 페이지네이션 성능 테스트 (기준선)")
class OffsetPaginationPerformanceTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            users.add(User.builder()
                    .email("user" + i + "@test.com")
                    .name("User " + i)
                    .passwordHash("hashed_password")
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .termsAgreedAt(now)
                    .privacyAgreedAt(now)
                    .build());
        }
        userRepository.saveAll(users);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("OFFSET 1페이지 응답 시간 측정")
    void offset_firstPage_measureTime() {
        long start = System.currentTimeMillis();
        Page<UserSearchDTO> result = adminUserService.searchUsers(
                null, null, null, null, null, 0, 10);
        long duration = System.currentTimeMillis() - start;

        log.info("🐢 OFFSET 1페이지: {}ms (총 {}건)", duration, result.getTotalElements());
        assertThat(result.getContent()).hasSize(10);
    }

    @Test
    @DisplayName("OFFSET 100페이지 응답 시간 측정")
    void offset_page100_measureTime() {
        long start = System.currentTimeMillis();
        Page<UserSearchDTO> result = adminUserService.searchUsers(
                null, null, null, null, null, 99, 10);
        long duration = System.currentTimeMillis() - start;

        log.info("🐢 OFFSET 100페이지: {}ms", duration);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("OFFSET 마지막 페이지(1000건 기준) 응답 시간 측정")
    void offset_lastPage_measureTime() {
        long start = System.currentTimeMillis();
        Page<UserSearchDTO> result = adminUserService.searchUsers(
                null, null, null, null, null, 99, 10);
        long duration = System.currentTimeMillis() - start;

        log.info("🐢 OFFSET 마지막 페이지(OFFSET 990): {}ms", duration);
        assertThat(result).isNotNull();
    }
}
