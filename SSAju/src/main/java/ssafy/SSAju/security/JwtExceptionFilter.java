package ssafy.SSAju.security;

import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.ErrorInfo;

import java.io.IOException;
import java.util.UUID;

/**
 * JwtAuthenticationFilter 앞단에 위치하여 JWT 파싱 라이브러리 예외를 HTTP 401 JSON으로 변환.
 * @ControllerAdvice는 Filter 예외를 처리하지 못하므로 별도 필터로 처리.
 */
@Slf4j
public class JwtExceptionFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public JwtExceptionFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            log.warn("JWT 예외 발생: type={}, path={}", e.getClass().getSimpleName(), request.getRequestURI());
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "AUTH-001", "유효하지 않은 토큰입니다.");
        }
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status,
                                    String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");

        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        ApiResponse<Void> body = ApiResponse.failure(new ErrorInfo(code, message, requestId));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
