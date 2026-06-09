package ssafy.SSAju.admin.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AdminAccessDeniedHandler implements AccessDeniedHandler {

    private static final String AJAX_HEADER = "X-Requested-With";
    private static final String AJAX_VALUE = "XMLHttpRequest";
    private static final String ACCESS_DENIED_JSON = "{\"code\":\"AUTH-003\",\"message\":\"접근 권한이 없습니다.\"}";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        if (AJAX_VALUE.equals(request.getHeader(AJAX_HEADER))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(ACCESS_DENIED_JSON);
        } else {
            response.sendRedirect("/admin/login");
        }
    }
}
