package ssafy.SSAju.util;

import jakarta.servlet.http.HttpServletRequest;

public class ClientIpUtil {

    private ClientIpUtil() {}

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
