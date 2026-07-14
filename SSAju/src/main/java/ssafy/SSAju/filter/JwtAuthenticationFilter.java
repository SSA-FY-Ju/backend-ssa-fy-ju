package ssafy.SSAju.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import ssafy.SSAju.security.AbstractJwtValidationFilter;
import ssafy.SSAju.security.redis.AccessTokenBlacklistService;
import ssafy.SSAju.util.BearerTokenUtil;
import ssafy.SSAju.util.JwtUtil;

@Slf4j
public class JwtAuthenticationFilter extends AbstractJwtValidationFilter {

    public JwtAuthenticationFilter(JwtUtil jwtUtil, AccessTokenBlacklistService blacklistService) {
        super(jwtUtil, blacklistService);
    }

    @Override
    protected String extractToken(HttpServletRequest request) {
        return BearerTokenUtil.extractBearerToken(request);
    }

    @Override
    protected void onValidationFailure(HttpServletRequest request, HttpServletResponse response, RuntimeException ex) {
        log.warn("Access token 검증 실패: {}", ex.getMessage());
        super.onValidationFailure(request, response, ex);
    }
}
