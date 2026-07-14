package ssafy.SSAju.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * {@code Authorization} 헤더에서 Bearer 토큰을 추출하는 유틸리티.
 *
 * <p>AuthController, UserController, JwtAuthenticationFilter, AdminCookieJwtFilter 등
 * 여러 곳에서 동일한 파싱 로직이 중복되던 것을 하나로 모았다.
 */
public final class BearerTokenUtil {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private BearerTokenUtil() {}

    /**
     * 요청의 {@code Authorization} 헤더에서 Bearer 토큰 원본값을 추출한다.
     *
     * <p>스킴({@code Bearer})은 RFC 7235에 따라 대소문자를 구분하지 않고 인식하며,
     * 헤더 값과 스킴 이후의 토큰 모두 앞뒤 공백을 제거한 뒤 비교/반환한다.
     *
     * @param request HTTP 요청
     * @return 토큰 원본값, 헤더가 없거나 Bearer 형식이 아니거나 토큰이 비어 있으면 {@code null}
     */
    public static String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(header)) {
            return null;
        }

        String trimmedHeader = header.trim();
        if (!trimmedHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }

        String token = trimmedHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
