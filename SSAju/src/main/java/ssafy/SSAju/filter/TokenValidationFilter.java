package ssafy.SSAju.filter;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.ErrorInfo;
import ssafy.SSAju.entity.RefreshToken;
import ssafy.SSAju.repository.RefreshTokenRepository;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class TokenValidationFilter extends OncePerRequestFilter {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final String REFRESH_ENDPOINT = "/api/auth/refresh";

    private final RefreshTokenRepository refreshTokenRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !REFRESH_ENDPOINT.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tokenValue = extractRefreshTokenFromCookie(request);

        if (tokenValue == null) {
            sendErrorResponse(response, "리프레시 토큰이 없습니다.");
            return;
        }

        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenValue);

        if (tokenOpt.isEmpty() || tokenOpt.get().isRevoked() || tokenOpt.get().isExpired()) {
            log.warn("유효하지 않은 RefreshToken으로 갱신 시도");
            sendErrorResponse(response, "유효하지 않은 리프레시 토큰입니다.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_TOKEN_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        ApiResponse<Void> body = ApiResponse.failure(new ErrorInfo("INVALID_TOKEN", message, requestId));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
