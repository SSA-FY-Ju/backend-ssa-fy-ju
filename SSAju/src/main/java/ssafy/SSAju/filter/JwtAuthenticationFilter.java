package ssafy.SSAju.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import ssafy.SSAju.security.AbstractJwtValidationFilter;
import ssafy.SSAju.security.redis.AccessTokenBlacklistService;
import ssafy.SSAju.util.JwtUtil;

@Slf4j
public class JwtAuthenticationFilter extends AbstractJwtValidationFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    public JwtAuthenticationFilter(JwtUtil jwtUtil, AccessTokenBlacklistService blacklistService) {
        super(jwtUtil, blacklistService);
    }

    @Override
    protected String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    @Override
    protected void onValidationFailure(HttpServletRequest request, HttpServletResponse response, RuntimeException ex) {
        log.warn("Access token 검증 실패: {}", ex.getMessage());
        super.onValidationFailure(request, response, ex);
    }
}
