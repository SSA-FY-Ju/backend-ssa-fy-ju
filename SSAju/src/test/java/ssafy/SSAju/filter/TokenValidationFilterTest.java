package ssafy.SSAju.filter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ssafy.SSAju.exception.InvalidTokenException;
import ssafy.SSAju.security.redis.RefreshTokenRedisRepository;
import ssafy.SSAju.util.JwtUtil;
import ssafy.SSAju.util.TokenHashUtil;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * TokenValidationFilter 단위 테스트 (T013).
 * /api/auth/refresh, /api/auth/logout 이외 경로는 쿠키 없이도 필터를 통과하는지,
 * 두 경로에서는 RefreshToken 쿠키를 검증하는지 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenValidationFilter 단위 테스트 (T013)")
class TokenValidationFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private RefreshTokenRedisRepository refreshTokenRedisRepository;

    private TokenValidationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TokenValidationFilter(jwtUtil, refreshTokenRedisRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("T013-1: /api/auth/refresh 는 필터가 적용된다")
    void shouldNotFilter_refreshEndpoint_returnsFalse() {
        assertThat(filter.shouldNotFilter(requestWithServletPath("/api/auth/refresh"))).isFalse();
    }

    @Test
    @DisplayName("T013-2: /api/auth/logout 는 필터가 적용된다")
    void shouldNotFilter_logoutEndpoint_returnsFalse() {
        assertThat(filter.shouldNotFilter(requestWithServletPath("/api/auth/logout"))).isFalse();
    }

    @Test
    @DisplayName("T013-3: 그 외 경로(/api/mypage 등)는 RefreshToken 쿠키 없이도 필터를 건너뛴다")
    void shouldNotFilter_otherEndpoints_returnsTrue() {
        assertThat(filter.shouldNotFilter(requestWithServletPath("/api/mypage"))).isTrue();
        assertThat(filter.shouldNotFilter(requestWithServletPath("/api/auth/login"))).isTrue();
        assertThat(filter.shouldNotFilter(requestWithServletPath("/api/auth/signup"))).isTrue();
    }

    private MockHttpServletRequest requestWithServletPath(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        return request;
    }

    @Test
    @DisplayName("T013-4: RefreshToken 쿠키가 없으면 401을 반환하고 체인을 진행하지 않는다")
    void doFilterInternal_noCookie_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("T013-5: Redis에 대응하는 항목이 없으면(로그아웃/회전으로 삭제됨) 401을 반환한다")
    void doFilterInternal_notFoundInRedis_returns401() throws Exception {
        String tokenValue = "some-refresh-token";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.setCookies(new Cookie("refreshToken", tokenValue));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        Claims claims = mock(Claims.class);
        given(claims.get("jti", String.class)).willReturn("jti-1");
        given(jwtUtil.parseAndValidateClaims(tokenValue)).willReturn(claims);
        given(refreshTokenRedisRepository.find("jti-1")).willReturn(Optional.empty());

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("T013-6: 위조/만료된 RefreshToken이면 401을 반환한다")
    void doFilterInternal_invalidJwt_returns401() throws Exception {
        String tokenValue = "malformed-token";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
        request.setCookies(new Cookie("refreshToken", tokenValue));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        given(jwtUtil.parseAndValidateClaims(tokenValue)).willThrow(new InvalidTokenException("위조된 토큰"));

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("T013-7: 유효한 RefreshToken 쿠키가 있으면 필터 체인을 진행한다")
    void doFilterInternal_validCookie_proceedsFilterChain() throws Exception {
        String tokenValue = "valid-refresh-token";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.setCookies(new Cookie("refreshToken", tokenValue));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        Claims claims = mock(Claims.class);
        given(claims.get("jti", String.class)).willReturn("jti-1");
        given(jwtUtil.parseAndValidateClaims(tokenValue)).willReturn(claims);
        given(refreshTokenRedisRepository.find("jti-1")).willReturn(
                Optional.of(new RefreshTokenRedisRepository.StoredRefreshToken(1L, TokenHashUtil.sha256(tokenValue))));

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
