package ssafy.SSAju.filter;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
import ssafy.SSAju.util.CookieUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * RefreshToken 쿠키 검증 필터.
 *
 * {@code /api/auth/refresh} 엔드포인트 요청에 대해 RefreshToken 유효성을 검증합니다.
 * 토큰이 없거나, revoked, 또는 expired 상태면 401 Unauthorized JSON 응답을 반환합니다.
 *
 * <p><b>필터 체인 위치:</b>
 * {@link JwtAuthenticationFilter} 다음에 위치하여 기본 JWT 검증 후 RefreshToken만 추가 검증합니다.
 *
 * @author SSAju Team
 * @see SecurityConfig
 * @see JwtAuthenticationFilter
 */
@Slf4j
@RequiredArgsConstructor
public class TokenValidationFilter extends OncePerRequestFilter {

    private static final String REFRESH_ENDPOINT = "/api/auth/refresh";

    private final RefreshTokenRepository refreshTokenRepository;
    private final ObjectMapper objectMapper;

    /**
     * /api/auth/refresh 엔드포인트에만 이 필터를 적용합니다.
     *
     * @param request HTTP 요청
     * @return /api/auth/refresh가 아닌 경우 true (필터 스킵)
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !REFRESH_ENDPOINT.equals(request.getRequestURI());
    }

    /**
     * RefreshToken 쿠키를 검증하고 필터 체인을 진행합니다.
     *
     * <p><b>검증 로직:</b>
     * <ol>
     *   <li>쿠키에서 RefreshToken 추출</li>
     *   <li>토큰 존재 여부 확인</li>
     *   <li>데이터베이스에서 토큰 조회</li>
     *   <li>revoked 상태 확인</li>
     *   <li>만료 시간 확인</li>
     * </ol>
     *
     * 검증 실패 시 401 Unauthorized JSON 응답을 반환합니다.
     *
     * @param request HTTP 요청
     * @param response HTTP 응답
     * @param filterChain 필터 체인
     * @throws ServletException 서블릿 처리 예외
     * @throws IOException I/O 예외
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tokenValue = CookieUtil.getRefreshTokenFromCookie(request);

        if (tokenValue == null) {
            sendErrorResponse(response, "리프레시 토큰이 없습니다.");
            return;
        }

        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(hashToken(tokenValue));

        if (tokenOpt.isEmpty() || tokenOpt.get().isRevoked() || tokenOpt.get().isExpired()) {
            log.warn("유효하지 않은 RefreshToken으로 갱신 시도");
            sendErrorResponse(response, "유효하지 않은 리프레시 토큰입니다.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        ApiResponse<Void> body = ApiResponse.failure(new ErrorInfo("INVALID_TOKEN", message, requestId));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
