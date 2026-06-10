package ssafy.SSAju.admin.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import ssafy.SSAju.exception.InvalidTokenException;
import ssafy.SSAju.exception.TokenExpiredException;
import ssafy.SSAju.util.JwtUtil;
import ssafy.SSAju.util.TokenType;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 관리자 SSR 페이지 전용 JWT 인증 필터.
 *
 * <p>브라우저 페이지 이동 시 Authorization 헤더 대신
 * {@code admin_access_token} 쿠키에서 JWT를 추출하여 인증합니다.
 * Authorization 헤더가 있으면 헤더를 우선 사용합니다 (AJAX 지원).
 */
@Slf4j
public class AdminCookieJwtFilter extends OncePerRequestFilter {

    public static final String ADMIN_TOKEN_COOKIE = "admin_access_token";
    public static final String ADMIN_REFRESH_TOKEN_COOKIE = "admin_refresh_token";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final boolean cookieSecure;

    public AdminCookieJwtFilter(JwtUtil jwtUtil, boolean cookieSecure) {
        this.jwtUtil = jwtUtil;
        this.cookieSecure = cookieSecure;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            try {
                Claims claims = jwtUtil.parseAndValidateClaims(token);
                validateAccessTokenType(claims);
                setAuthentication(claims);
            } catch (TokenExpiredException | InvalidTokenException ex) {
                log.warn("관리자 토큰 검증 실패: {}", ex.getMessage());
                clearAdminCookie(response);
                request.setAttribute("jwtException", ex);
            } catch (JwtException ex) {
                log.warn("관리자 JWT 처리 실패: {}", ex.getMessage());
                clearAdminCookie(response);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        // 1순위: Authorization 헤더 (AJAX 요청)
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        // 2순위: 쿠키 (브라우저 SSR 페이지 이동)
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(c -> ADMIN_TOKEN_COOKIE.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private void validateAccessTokenType(Claims claims) {
        String typeValue = (String) claims.get("type");
        if (!TokenType.ACCESS.getValue().equals(typeValue)) {
            throw new InvalidTokenException("Access token만 사용 가능합니다.");
        }
        if (!claims.containsKey("userId")) {
            throw new InvalidTokenException("필수 클레임이 누락되었습니다.");
        }
    }

    private void setAuthentication(Claims claims) {
        Long userId = claims.get("userId", Long.class);
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);
        String authority = (role != null) ? "ROLE_" + role : "ROLE_USER";

        var auth = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority(authority))
        );
        auth.setDetails(email);
        SecurityContextHolder.getContext().setAuthentication(auth);
        log.debug("관리자 쿠키 인증 성공: userId={}, role={}", userId, authority);
    }

    private void clearAdminCookie(HttpServletResponse response) {
        ResponseCookie expired = ResponseCookie.from(ADMIN_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/admin")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
    }
}
