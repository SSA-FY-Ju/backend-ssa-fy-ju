package ssafy.SSAju.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 클라이언트 IP 주소 추출 유틸리티.
 *
 * 리버스 프록시(AWS ALB, Nginx, Cloudflare) 환경에서 실제 클라이언트 IP를 추출합니다.
 * 여러 프록시 헤더를 확인하여 가장 신뢰할 수 있는 IP를 반환합니다.
 *
 * <p>헤더 확인 순서:
 * <ol>
 *   <li>{@code X-Forwarded-For} - AWS ALB, Nginx 등 일반적인 프록시 헤더</li>
 *   <li>{@code CF-Connecting-IP} - Cloudflare 프록시 헤더</li>
 *   <li>{@code X-Real-IP} - 기타 프록시 헤더</li>
 *   <li>{@code request.getRemoteAddr()} - 직접 연결된 클라이언트 IP</li>
 * </ol>
 *
 * @author SSAju Team
 */
public class ClientIpUtil {

    private ClientIpUtil() {}

    /**
     * HTTP 요청에서 실제 클라이언트 IP 주소를 추출합니다.
     *
     * <p>X-Forwarded-For 헤더에는 쉼표로 구분된 IP 목록이 포함될 수 있으므로,
     * 첫 번째 IP를 추출합니다. (클라이언트 IP)
     *
     * @param request HTTP 요청 객체
     * @return 클라이언트 IP 주소
     */
    public static String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        String cfConnecting = request.getHeader("CF-Connecting-IP");
        if (hasText(cfConnecting)) {
            return cfConnecting;
        }

        String realIp = request.getHeader("X-Real-IP");
        if (hasText(realIp)) {
            return realIp;
        }

        return request.getRemoteAddr();
    }

    private static boolean hasText(String str) {
        return str != null && !str.isBlank();
    }
}
