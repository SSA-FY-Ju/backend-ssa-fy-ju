package ssafy.SSAju.admin.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AdminAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String AJAX_HEADER = "X-Requested-With";
    private static final String AJAX_VALUE = "XMLHttpRequest";
    private static final String AUTH_REQUIRED_JSON = "{\"code\":\"AUTH-001\",\"message\":\"인증이 필요합니다.\"}";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        if (AJAX_VALUE.equals(request.getHeader(AJAX_HEADER))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(AUTH_REQUIRED_JSON);
        } else {
            response.sendRedirect("/admin/login");
        }
    }
}
