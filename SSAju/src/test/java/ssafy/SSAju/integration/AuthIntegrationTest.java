package ssafy.SSAju.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ssafy.SSAju.repository.RefreshTokenRepository;
import ssafy.SSAju.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 플로우 통합 테스트 (T044).
 * 회원가입 → 로그인 → API 호출 → 토큰 갱신 → 로그아웃 전체 플로우 검증.
 * H2 인메모리 DB 사용 (인증 플로우는 MySQL 전용 SQL 없음).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("인증 플로우 통합 테스트 (T044)")
class AuthIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

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
    @DisplayName("T044-1: 회원가입 성공 → 201 Created")
    void signup_success() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("auth-test@example.com", "password123", "테스터")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("T044-2: 중복 이메일 회원가입 → 409 Conflict")
    void signup_duplicateEmail_returns409() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("dup@example.com", "password123", "사용자")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("dup@example.com", "password123", "사용자2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("T044-3: 로그인 성공 → AccessToken + RefreshToken 발급")
    void login_success_returnsTokens() throws Exception {
        signup("login-test@example.com", "password123", "로그인테스터");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("login-test@example.com", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").isNumber())
                .andReturn();

        assertThat(result.getResponse().getHeader("Authorization")).startsWith("Bearer ");
        assertThat(result.getResponse().getHeader("Refresh-Token")).isNotBlank();
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("T044-4: 잘못된 비밀번호 로그인 → 401 Unauthorized")
    void login_wrongPassword_returns401() throws Exception {
        signup("fail@example.com", "correctPass1", "사용자");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("fail@example.com", "wrongPass!!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("T044-5: AccessToken으로 보호된 API 호출 → 인증 성공 (401 아님)")
    void protectedEndpoint_withValidToken_notUnauthorized() throws Exception {
        signup("protected@example.com", "password123", "보호테스터");
        String[] tokens = loginAndGetTokens("protected@example.com", "password123");

        // /api/mypage/analyses/{id}?type=SAJU : 인증 성공이면 404 (존재하지 않는 ID), 인증 실패면 401
        mockMvc.perform(get("/api/mypage/analyses/999999")
                        .param("type", "SAJU")
                        .header("Authorization", tokens[0]))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("T044-6: AccessToken 없이 보호된 API 호출 → 401 Unauthorized")
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/mypage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("T044-7: RefreshToken으로 AccessToken 갱신 → 새 토큰 발급")
    void tokenRefresh_withValidRefreshToken_returnsNewAccessToken() throws Exception {
        signup("refresh@example.com", "password123", "갱신테스터");
        String[] tokens = loginAndGetTokens("refresh@example.com", "password123");
        String refreshToken = tokens[1];

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .header("Refresh-Token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").isNumber())
                .andReturn();

        assertThat(result.getResponse().getHeader("Authorization")).startsWith("Bearer ");
    }

    @Test
    @DisplayName("T044-8: 로그아웃 → RefreshToken revoked, 이후 갱신 시도 실패")
    void logout_thenRefresh_returns401() throws Exception {
        signup("logout@example.com", "password123", "로그아웃테스터");
        String[] tokens = loginAndGetTokens("logout@example.com", "password123");
        String accessToken = tokens[0];
        String refreshToken = tokens[1];

        // 로그아웃
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", accessToken)
                        .header("Refresh-Token", refreshToken))
                .andExpect(status().isOk());

        // 로그아웃 후 RefreshToken으로 갱신 시도 → 실패
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Refresh-Token", refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("T044-9: 회원가입 → 로그인 → 토큰 갱신 → 로그아웃 전체 플로우")
    void fullAuthFlow_signupLoginRefreshLogout() throws Exception {
        // 1. 회원가입
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("full-flow@example.com", "password123", "전체플로우")))
                .andExpect(status().isCreated());

        // 2. 로그인
        String[] tokens = loginAndGetTokens("full-flow@example.com", "password123");
        String accessToken = tokens[0];
        String refreshToken = tokens[1];

        // 3. 보호된 API 호출 성공 (인증 성공이면 404, 실패면 401)
        mockMvc.perform(get("/api/mypage/analyses/999999")
                        .param("type", "SAJU")
                        .header("Authorization", accessToken))
                .andExpect(status().isNotFound());

        // 4. 토큰 갱신
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .header("Refresh-Token", refreshToken))
                .andExpect(status().isOk())
                .andReturn();
        String newAccessToken = refreshResult.getResponse().getHeader("Authorization");

        // 5. 갱신된 토큰으로 API 호출 성공 (인증 성공이면 404, 실패면 401)
        mockMvc.perform(get("/api/mypage/analyses/999999")
                        .param("type", "SAJU")
                        .header("Authorization", newAccessToken))
                .andExpect(status().isNotFound());

        // 6. 로그아웃
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", newAccessToken)
                        .header("Refresh-Token", refreshToken))
                .andExpect(status().isOk());

        // 7. 로그아웃 후 API 호출 → RefreshToken은 revoked, 기존 AccessToken은 만료 전까지 유효
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Refresh-Token", refreshToken))
                .andExpect(status().isUnauthorized());
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private void signup(String email, String password, String name) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email, password, name)));
    }

    private String[] loginAndGetTokens(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = result.getResponse().getHeader("Authorization");
        String refreshToken = result.getResponse().getHeader("Refresh-Token");
        return new String[]{accessToken, refreshToken};
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
