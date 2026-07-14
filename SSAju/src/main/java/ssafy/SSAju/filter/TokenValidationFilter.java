package ssafy.SSAju.filter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import ssafy.SSAju.exception.InvalidTokenException;
import ssafy.SSAju.exception.TokenExpiredException;
import ssafy.SSAju.security.redis.RefreshTokenRedisRepository;
import ssafy.SSAju.util.CookieUtil;
import ssafy.SSAju.util.JwtUtil;
import ssafy.SSAju.util.TokenHashUtil;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * RefreshToken 쿠키 검증 필터.
 *
 * {@code /api/auth/refresh}, {@code /api/auth/logout} 요청에 대해서만 Refresh Token 쿠키의
 * 유효성(서명/만료/Redis 존재 여부)을 검증합니다. 그 외 모든 경로는 이 필터를 완전히 건너뛰며
 * Refresh Token 쿠키의 존재 여부와 무관하게 동작합니다(FR-005).
 *
 * <p>토큰이 없거나, 위조/만료되었거나, Redis에 대응하는 키가 없으면(로그아웃/회전으로 삭제됨 포함)
 * 401 Unauthorized JSON 응답을 반환합니다.
 *
 * @author SSAju Team
 * @see SecurityConfig
 * @see JwtAuthenticationFilter
 */
@Slf4j
@RequiredArgsConstructor
public class TokenValidationFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_ENDPOINTS = Set.of("/api/auth/refresh", "/api/auth/logout");

    private final JwtUtil jwtUtil;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final HandlerExceptionResolver exceptionResolver;

    /**
     * {@code /api/auth/refresh}, {@code /api/auth/logout}에만 이 필터를 적용합니다.
     *
     * @param request HTTP 요청
     * @return 두 엔드포인트가 아닌 경우 true (필터 스킵)
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PROTECTED_ENDPOINTS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tokenValue = CookieUtil.getRefreshTokenFromCookie(request);

        if (tokenValue == null) {
            exceptionResolver.resolveException(request, response, null,
                    new InvalidTokenException("리프레시 토큰이 없습니다."));
            return;
        }

        Claims claims;
        try {
            claims = jwtUtil.parseAndValidateClaims(tokenValue);
        } catch (TokenExpiredException | InvalidTokenException ex) {
            log.warn("RefreshToken 검증 실패: {}", ex.getMessage());
            exceptionResolver.resolveException(request, response, null, ex);
            return;
        }

        String jti = claims.get("jti", String.class);
        Optional<RefreshTokenRedisRepository.StoredRefreshToken> stored = refreshTokenRedisRepository.find(jti);

        if (stored.isEmpty() || !stored.get().tokenHash().equals(TokenHashUtil.sha256(tokenValue))) {
            log.warn("유효하지 않은 RefreshToken으로 갱신/로그아웃 시도");
            exceptionResolver.resolveException(request, response, null,
                    new InvalidTokenException("유효하지 않은 리프레시 토큰입니다."));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
