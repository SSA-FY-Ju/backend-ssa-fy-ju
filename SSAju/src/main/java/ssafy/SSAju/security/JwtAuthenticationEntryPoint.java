package ssafy.SSAju.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;
import ssafy.SSAju.exception.InvalidTokenException;

import java.io.IOException;

/**
 * 미인증 접근 시 401 JSON 응답을 반환한다.
 *
 * <p>{@link AbstractJwtValidationFilter}가 토큰 검증 실패 시 남겨둔 구체적 원인
 * ({@code request} 속성 {@code jwtException})이 있으면 그대로 사용하고, 없으면(토큰 자체가
 * 없는 일반적인 미인증 접근) 일반 {@link InvalidTokenException}으로 대체한다. 두 경우 모두
 * {@link HandlerExceptionResolver}를 통해 {@link ssafy.SSAju.handler.SajuGlobalExceptionHandler}로
 * 위임하여 응답 형식을 통일한다.
 */
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String JWT_EXCEPTION_ATTRIBUTE = "jwtException";

    private final HandlerExceptionResolver exceptionResolver;

    public JwtAuthenticationEntryPoint(HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("인증되지 않은 접근: path={}", request.getRequestURI());

        Object cause = request.getAttribute(JWT_EXCEPTION_ATTRIBUTE);
        RuntimeException resolved = cause instanceof RuntimeException runtimeException
                ? runtimeException
                : new InvalidTokenException("인증이 필요합니다.");
        exceptionResolver.resolveException(request, response, null, resolved);
    }
}
