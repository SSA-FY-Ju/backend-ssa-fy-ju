package ssafy.SSAju.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import ssafy.SSAju.service.DailyApiUsageService;

import java.util.List;

public class APIUsageInterceptor implements HandlerInterceptor {

    private static final List<String> LIMITED_API_PATHS = List.of(
            "/api/career/consultation",
            "/api/career/timing",
            "/api/career/compatibility",
            "/api/mypage/reanalyze"
    );

    private final DailyApiUsageService dailyApiUsageService;

    public APIUsageInterceptor(DailyApiUsageService dailyApiUsageService) {
        this.dailyApiUsageService = dailyApiUsageService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();

        if (LIMITED_API_PATHS.stream().noneMatch(path::startsWith)) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Long userId)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}}");
            return false;
        }

        dailyApiUsageService.checkAndIncrementDailyUsage(userId);
        return true;
    }
}
