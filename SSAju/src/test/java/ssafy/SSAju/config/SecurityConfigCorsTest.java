package ssafy.SSAju.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS allowedHeaders 화이트리스트 검증 (T027, User Story 4).
 *
 * <p>화이트리스트 밖 임의 헤더로 preflight 요청 시 거부되고, 화이트리스트에 등록된
 * 헤더(Authorization/Content-Type/x-vercel-proxy)로는 정상 허용됨을 검증한다
 * (quickstart.md "US4").
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("CORS 헤더 화이트리스트 테스트 (T027)")
class SecurityConfigCorsTest {

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("화이트리스트 밖 임의 헤더로 preflight 요청 시 거부됨")
    void shouldRejectPreflight_WhenHeaderNotWhitelisted() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-Arbitrary-Header"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authorization/Content-Type 헤더로 preflight 요청 시 허용됨")
    void shouldAllowPreflight_WhenHeadersWhitelisted() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", "Authorization, Content-Type"));
    }

    @Test
    @DisplayName("x-vercel-proxy 헤더로 preflight 요청 시 허용됨 (프론트 Vercel 프록시 연동)")
    void shouldAllowPreflight_WhenVercelProxyHeaderRequested() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-vercel-proxy"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", "x-vercel-proxy"));
    }
}
