package ssafy.SSAju.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.repository.RefreshTokenRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Soft Delete 통합 테스트 (T046).
 * 회원 탈퇴 후 @SQLRestriction(deleted_at IS NULL) 필터링과
 * 개인정보 마스킹(이름, 이메일)을 검증합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("Soft Delete 통합 테스트 (T046)")
class SoftDeleteIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("T046-1: 탈퇴 후 userRepository.findById → @SQLRestriction으로 조회 불가")
    void deleteUser_thenFindById_returnsEmpty() throws Exception {
        signup("soft-delete@example.com", "password123", "탈퇴테스터");
        String[] tokens = loginAndGetTokens("soft-delete@example.com", "password123");
        Long userId = userRepository.findByEmail("soft-delete@example.com").map(User::getId).orElseThrow();

        deleteAccount(tokens[0], "password123");

        // @SQLRestriction("deleted_at IS NULL")으로 인해 삭제된 사용자는 findById 조회 불가
        Optional<User> found = userRepository.findById(userId);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("T046-2: 탈퇴 후 개인정보 마스킹 - 이름은 '탈퇴한 사용자', 이메일은 deleted_ 접두사")
    void deleteUser_personalInfoMasked() throws Exception {
        signup("masking@example.com", "password123", "마스킹테스터");
        String[] tokens = loginAndGetTokens("masking@example.com", "password123");
        Long userId = userRepository.findByEmail("masking@example.com").map(User::getId).orElseThrow();

        deleteAccount(tokens[0], "password123");

        // JdbcTemplate native query로 @SQLRestriction 우회 → 실제 마스킹 값 직접 검증
        String maskedName = jdbcTemplate.queryForObject(
                "SELECT name FROM users WHERE id = ? AND deleted_at IS NOT NULL",
                String.class, userId
        );
        String maskedEmail = jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE id = ? AND deleted_at IS NOT NULL",
                String.class, userId
        );

        // User.softDelete()의 마스킹 패턴 검증
        assertThat(maskedName).isEqualTo("탈퇴한 사용자");
        assertThat(maskedEmail)
                .startsWith("deleted_" + userId + "_")
                .endsWith("@deleted.local");

        // @SQLRestriction 필터링 확인: 탈퇴한 사용자는 JPA 조회 불가
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("T046-3: 탈퇴 후 로그인 시도 → 401 Unauthorized")
    void deleteUser_thenLogin_returns401() throws Exception {
        signup("deleted-login@example.com", "password123", "삭제후로그인");
        String[] tokens = loginAndGetTokens("deleted-login@example.com", "password123");

        deleteAccount(tokens[0], "password123");

        // 탈퇴 후 동일 이메일로 로그인 시도 → 사용자가 없으므로 401
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("deleted-login@example.com", "password123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("T046-4: 탈퇴 후 RefreshToken revoked → 토큰 갱신 불가")
    void deleteUser_refreshTokenRevoked() throws Exception {
        signup("revoke@example.com", "password123", "토큰테스터");
        String[] tokens = loginAndGetTokens("revoke@example.com", "password123");
        String refreshToken = tokens[1];

        deleteAccount(tokens[0], "password123");

        // 탈퇴 후 RefreshToken으로 갱신 시도 → 401
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Refresh-Token", refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("T046-5: 탈퇴 후 동일 이메일로 재가입 가능 (마스킹으로 UNIQUE 충돌 없음)")
    void deleteUser_thenSignupWithSameEmail_succeeds() throws Exception {
        signup("reuse-email@example.com", "password123", "재가입테스터");
        String[] tokens = loginAndGetTokens("reuse-email@example.com", "password123");

        deleteAccount(tokens[0], "password123");

        // 동일 이메일로 재가입: 마스킹(deleted_xxx@deleted.local)으로 UNIQUE 충돌 없음
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("reuse-email@example.com", "password456", "재가입사용자")))
                .andExpect(status().isCreated());
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private void signup(String email, String password, String name) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email, password, name)))
                .andExpect(status().isCreated());
    }

    private String[] loginAndGetTokens(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return new String[]{
                result.getResponse().getHeader("Authorization"),
                result.getResponse().getHeader("Refresh-Token")
        };
    }

    private void deleteAccount(String accessToken, String password) throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"%s\"}".formatted(password)))
                .andExpect(status().isOk());
    }

    private String signupBody(String email, String password, String name) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "name": "%s",
                  "termsAgreed": true,
                  "privacyAgreed": true
                }
                """.formatted(email, password, name);
    }

    private String loginBody(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }
}
