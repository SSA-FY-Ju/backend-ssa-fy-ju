package ssafy.SSAju.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import ssafy.SSAju.exception.InvalidTokenException;

import java.io.IOException;

/**
 * JwtAuthenticationFilter 앞단에 위치하여 JWT 파싱 라이브러리 예외를 HTTP 401 JSON으로 변환.
 *
 * <p>필터에서 발생한 예외는 {@code @ControllerAdvice}가 직접 처리하지 못하므로,
 * {@link HandlerExceptionResolver}를 통해 {@link ssafy.SSAju.handler.SajuGlobalExceptionHandler}로
 * 위임하여 응답 형식(에러 코드/메시지)을 다른 예외 처리 경로와 통일한다.
 */
@Slf4j
public class JwtExceptionFilter extends OncePerRequestFilter {

    private final HandlerExceptionResolver exceptionResolver;

    public JwtExceptionFilter(HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            log.warn("JWT 예외 발생: type={}, path={}", e.getClass().getSimpleName(), request.getRequestURI());
            exceptionResolver.resolveException(request, response, null,
                    new InvalidTokenException("유효하지 않은 토큰입니다."));
        }
    }
}
