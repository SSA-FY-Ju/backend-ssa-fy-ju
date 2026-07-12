package ssafy.SSAju.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 세션 보안 하드닝 통합 테스트 (T011, User Story 1).
 *
 * <p>로그인 → 보호 API 성공 → 로그아웃 → 동일 AccessToken 재사용 시 401,
 * 만료/로그아웃된 RefreshToken으로 갱신 시도 시 거부 및 Redis에 잔존 키 없음,
 * 일반 보호 API는 RefreshToken 쿠키 없이도 정상 동작함을 검증한다(quickstart.md "US1").
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("세션 보안 하드닝 통합 테스트 (T011)")
class AuthTokenSecurityIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        redissonClient.getKeys().flushall();
    }

    @Test
    @DisplayName("로그인 → 보호 API 성공 → 로그아웃 → 동일 AccessToken 재사용 시 401")
    void logout_invalidatesAccessTokenImmediately() throws Exception {
        signup("session-security@example.com", "password123", "세션보안테스터");
        MvcResult loginResult = login("session-security@example.com", "password123");
        String accessToken = loginResult.getResponse().getHeader("Authorization");
        String refreshTokenValue = extractRefreshTokenCookie(loginResult);

        mockMvc.perform(get("/api/mypage/analyses/999999")
                        .param("type", "SAJU")
                        .header("Authorization", accessToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", accessToken)
                        .cookie(new Cookie("refreshToken", refreshTokenValue)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/mypage/analyses/999999")
                        .param("type", "SAJU")
                        .header("Authorization", accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃 후 RefreshToken 갱신 거부 및 Redis에 잔존 키 없음")
    void logout_leavesNoResidualRedisKeys() throws Exception {
        signup("no-residue@example.com", "password123", "잔존키테스터");
        MvcResult loginResult = login("no-residue@example.com", "password123");
        String accessToken = loginResult.getResponse().getHeader("Authorization");
        String refreshTokenValue = extractRefreshTokenCookie(loginResult);

        // 로그인 직후에는 refresh-token: 키가 존재해야 한다
        assertThat(stringRedisTemplate.keys("refresh-token:*")).isNotEmpty();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", accessToken)
                        .cookie(new Cookie("refreshToken", refreshTokenValue)))
                .andExpect(status().isOk());

        // 로그아웃 후 RefreshToken 갱신 시도 → 거부
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", refreshTokenValue)))
                .andExpect(status().isUnauthorized());

        // 로그아웃한 RefreshToken의 refresh-token: 키는 Redis에 남아있지 않아야 한다
        assertThat(stringRedisTemplate.keys("refresh-token:*")).isEmpty();
    }

    @Test
    @DisplayName("일반 보호 API는 RefreshToken 쿠키 없이도 정상 동작한다 (FR-005)")
    void protectedEndpoint_worksWithoutRefreshTokenCookie() throws Exception {
        signup("no-cookie-needed@example.com", "password123", "쿠키불필요테스터");
        MvcResult loginResult = login("no-cookie-needed@example.com", "password123");
        String accessToken = loginResult.getResponse().getHeader("Authorization");

        // RefreshToken 쿠키를 전혀 싣지 않음
        mockMvc.perform(get("/api/mypage/analyses/999999")
                        .param("type", "SAJU")
                        .header("Authorization", accessToken))
                .andExpect(status().isNotFound());
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private void signup(String email, String password, String name) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email, password, name)))
                .andExpect(status().isCreated());
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String extractRefreshTokenCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("refreshToken");
        return cookie != null ? cookie.getValue() : null;
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
