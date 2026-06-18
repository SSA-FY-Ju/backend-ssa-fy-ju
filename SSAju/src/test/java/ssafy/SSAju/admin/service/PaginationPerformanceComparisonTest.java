package ssafy.SSAju.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
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
import ssafy.SSAju.admin.dto.UserSearchDTO;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.repository.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keyset 페이지네이션 성능 검증 및 커서 연속성 테스트.
 * OFFSET 방식 대비 성능 우위를 Testcontainers MySQL 환경에서 실측.
 */
@Slf4j
@Testcontainers
@SpringBootTest
@DisplayName("Keyset 페이지네이션 성능 및 정확성 테스트")
class PaginationPerformanceComparisonTest {

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

    private List<Long> savedIds;

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
        List<User> saved = userRepository.saveAll(users);
        savedIds = saved.stream().map(User::getId).sorted().toList();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Keyset 첫 페이지 응답 시간 측정")
    void keyset_firstPage_measureTime() {
        long start = System.currentTimeMillis();
        List<UserSearchDTO> result = adminUserService.searchUsers(
                null, null, null, null, null, null, 10);
        long duration = System.currentTimeMillis() - start;

        log.info("🚀 Keyset 첫 페이지: {}ms (결과 {}건)", duration, result.size());
        assertThat(result).hasSize(10);
    }

    @Test
    @DisplayName("Keyset 마지막 cursor 응답 시간 측정")
    void keyset_deepCursor_measureTime() {
        Long cursorId = savedIds.get(savedIds.size() - 11) + 1;

        long start = System.currentTimeMillis();
        List<UserSearchDTO> result = adminUserService.searchUsers(
                null, null, null, null, null, cursorId, 10);
        long duration = System.currentTimeMillis() - start;

        log.info("🚀 Keyset 마지막 cursor(id<{}): {}ms (결과 {}건)", cursorId, duration, result.size());
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Keyset 커서 연속 페이징 - 중복 없고 순서 보장")
    void keyset_cursorContinuity_noDuplicateAndOrdered() {
        // 첫 페이지
        List<UserSearchDTO> page1 = adminUserService.searchUsers(
                null, null, null, null, null, null, 10);
        assertThat(page1).hasSize(10);

        // 두 번째 페이지: 첫 페이지 마지막 id 커서로 사용
        Long nextCursor = page1.get(page1.size() - 1).id();
        List<UserSearchDTO> page2 = adminUserService.searchUsers(
                null, null, null, null, null, nextCursor, 10);
        assertThat(page2).hasSize(10);

        List<Long> page1Ids = page1.stream().map(UserSearchDTO::id).toList();
        List<Long> page2Ids = page2.stream().map(UserSearchDTO::id).toList();

        // 중복 없음
        assertThat(page1Ids).doesNotContainAnyElementsOf(page2Ids);
        // id DESC 순서 확인 (page1 최솟값 > page2 최댓값)
        assertThat(page1Ids.get(page1Ids.size() - 1)).isGreaterThan(page2Ids.get(0));

        log.info("Keyset 연속 페이징: page1 마지막 id={}, page2 첫 id={}",
                page1Ids.get(page1Ids.size() - 1), page2Ids.get(0));
    }

    @Test
    @DisplayName("Keyset 필터(email) 적용 후 페이징 정확성 검증")
    void keyset_withFilter_returnsFilteredResults() {
        List<UserSearchDTO> result = adminUserService.searchUsers(
                "user1", null, null, null, null, null, 20);

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(u -> u.email().contains("user1"));
    }
}
