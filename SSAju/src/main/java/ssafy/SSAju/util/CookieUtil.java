package ssafy.SSAju.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final long REFRESH_TOKEN_MAX_AGE_SECONDS = 604800L; // 7일

    @Value("${server.cookie.secure:true}")
    private boolean secureCookie;

    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie
                .from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .maxAge(REFRESH_TOKEN_MAX_AGE_SECONDS)
                .path("/")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
                .from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .maxAge(0)
                .path("/")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
