package ssafy.SSAju.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.ErrorInfo;

import java.io.IOException;
import java.util.UUID;

@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("인증되지 않은 접근: path={}", request.getRequestURI());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");

        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        ApiResponse<Void> body = ApiResponse.failure(
                new ErrorInfo("AUTH-001", "인증이 필요합니다.", requestId));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
