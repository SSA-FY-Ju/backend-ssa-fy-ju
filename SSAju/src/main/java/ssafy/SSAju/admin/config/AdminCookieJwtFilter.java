package ssafy.SSAju.admin.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
import ssafy.SSAju.security.AbstractJwtValidationFilter;
import ssafy.SSAju.security.redis.AccessTokenBlacklistService;
import ssafy.SSAju.util.BearerTokenUtil;
import ssafy.SSAju.util.JwtUtil;

import java.util.Arrays;

/**
 * 관리자 SSR 페이지 전용 JWT 인증 필터.
 *
 * <p>브라우저 페이지 이동 시 Authorization 헤더 대신
 * {@code admin_access_token} 쿠키에서 JWT를 추출하여 인증합니다.
 * Authorization 헤더가 있으면 헤더를 우선 사용합니다 (AJAX 지원).
 */
@Slf4j
public class AdminCookieJwtFilter extends AbstractJwtValidationFilter {

    public static final String ADMIN_TOKEN_COOKIE = "admin_access_token";
    public static final String ADMIN_REFRESH_TOKEN_COOKIE = "admin_refresh_token";

    private final boolean cookieSecure;

    public AdminCookieJwtFilter(JwtUtil jwtUtil, AccessTokenBlacklistService blacklistService, boolean cookieSecure) {
        super(jwtUtil, blacklistService);
        this.cookieSecure = cookieSecure;
    }

    @Override
    protected String extractToken(HttpServletRequest request) {
        // 1순위: Authorization 헤더 (AJAX 요청)
        String bearerToken = BearerTokenUtil.extractBearerToken(request);
        if (StringUtils.hasText(bearerToken)) {
            return bearerToken;
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

    @Override
    protected void onValidationFailure(HttpServletRequest request, HttpServletResponse response, RuntimeException ex) {
        log.warn("관리자 토큰 검증 실패: {}", ex.getMessage());
        clearAdminCookie(response);
        super.onValidationFailure(request, response, ex);
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
