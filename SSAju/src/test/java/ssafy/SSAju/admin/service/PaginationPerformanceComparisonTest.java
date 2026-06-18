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
@DisplayName("OFFSET vs Keyset 페이지네이션 성능 비교 테스트")
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
    @DisplayName("OFFSET vs Keyset 성능 비교 - 첫 페이지")
    void compare_firstPage() {
        // OFFSET: 첫 페이지 (OFFSET 0)
        long offsetStart = System.currentTimeMillis();
        Page<UserSearchDTO> offsetResult = adminUserService.searchUsers(
                null, null, null, null, null, 0, 10);
        long offsetDuration = System.currentTimeMillis() - offsetStart;

        // Keyset: 첫 페이지 (cursor null)
        long keysetStart = System.currentTimeMillis();
        List<UserSearchDTO> keysetResult = adminUserService.searchUsersWithKeyset(
                null, null, null, null, null, null, 10);
        long keysetDuration = System.currentTimeMillis() - keysetStart;

        log.info("\n╔════════════════════════════════════════════╗");
        log.info("║  첫 페이지 성능 비교 (1000건 기준)");
        log.info("║  🐢 OFFSET 방식: {}ms", offsetDuration);
        log.info("║  🚀 Keyset 방식: {}ms", keysetDuration);
        log.info("╚════════════════════════════════════════════╝");

        assertThat(offsetResult.getContent()).hasSize(10);
        assertThat(keysetResult).hasSize(10);
    }

    @Test
    @DisplayName("OFFSET vs Keyset 성능 비교 - 마지막 페이지(990번째 offset)")
    void compare_deepPage() {
        // OFFSET: 99페이지 (OFFSET 990) - 데이터가 많을수록 느려짐
        long offsetStart = System.currentTimeMillis();
        Page<UserSearchDTO> offsetResult = adminUserService.searchUsers(
                null, null, null, null, null, 99, 10);
        long offsetDuration = System.currentTimeMillis() - offsetStart;

        // Keyset: 마지막 근처 cursor (savedIds 중 뒤에서 11번째 id)
        Long cursorId = savedIds.get(savedIds.size() - 11);
        long keysetStart = System.currentTimeMillis();
        List<UserSearchDTO> keysetResult = adminUserService.searchUsersWithKeyset(
                null, null, null, null, null, cursorId + 1, 10);
        long keysetDuration = System.currentTimeMillis() - keysetStart;

        double improvement = offsetDuration > 0
                ? ((offsetDuration - keysetDuration) * 100.0) / offsetDuration
                : 0;

        log.info("\n╔════════════════════════════════════════════╗");
        log.info("║  마지막 페이지 성능 비교 (OFFSET 990 기준)");
        log.info("║  🐢 OFFSET 방식:    {}ms", offsetDuration);
        log.info("║  🚀 Keyset 방식:    {}ms", keysetDuration);
        log.info("║  ✨ 성능 개선율:   {}", String.format("%.1f%%", improvement));
        log.info("╚════════════════════════════════════════════╝");

        assertThat(offsetResult).isNotNull();
        assertThat(keysetResult).isNotNull();
    }

    @Test
    @DisplayName("Keyset 커서 연속 페이징 - 다음 페이지 cursor 정확성 검증")
    void keyset_cursorContinuity() {
        // 첫 페이지
        List<UserSearchDTO> page1 = adminUserService.searchUsersWithKeyset(
                null, null, null, null, null, null, 10);
        assertThat(page1).hasSize(10);

        // 두 번째 페이지: 첫 페이지 마지막 id를 cursor로
        Long nextCursor = page1.get(page1.size() - 1).id();
        List<UserSearchDTO> page2 = adminUserService.searchUsersWithKeyset(
                null, null, null, null, null, nextCursor, 10);
        assertThat(page2).hasSize(10);

        // 중복 없는지 확인
        List<Long> page1Ids = page1.stream().map(UserSearchDTO::id).toList();
        List<Long> page2Ids = page2.stream().map(UserSearchDTO::id).toList();
        assertThat(page1Ids).doesNotContainAnyElementsOf(page2Ids);

        log.info("Keyset 연속 페이징 검증: page1 last id={}, page2 first id={}",
                page1Ids.get(page1Ids.size() - 1), page2Ids.get(0));
    }
}
