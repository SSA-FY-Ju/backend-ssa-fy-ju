package ssafy.SSAju.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * 인증은 되었으나 권한이 없는 접근 시 403 JSON 응답을 반환한다.
 *
 * <p>{@link HandlerExceptionResolver}를 통해 {@link ssafy.SSAju.handler.SajuGlobalExceptionHandler}의
 * {@code AccessDeniedException} 처리기로 위임하여, 메서드 보안({@code @PreAuthorize})에서 발생한
 * 동일 예외와 응답 형식을 통일한다.
 */
@Slf4j
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final HandlerExceptionResolver exceptionResolver;

    public JwtAccessDeniedHandler(HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("권한 없는 접근: path={}", request.getRequestURI());
        exceptionResolver.resolveException(request, response, null, accessDeniedException);
    }
}
