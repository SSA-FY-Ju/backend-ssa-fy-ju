package ssafy.SSAju.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import ssafy.SSAju.exception.InvalidTokenException;
import ssafy.SSAju.exception.TokenExpiredException;
import ssafy.SSAju.util.JwtUtil;
import ssafy.SSAju.util.TokenType;

import java.io.IOException;
import java.util.List;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            try {
                Claims claims = jwtUtil.parseAndValidateClaims(token);
                validateTokenType(claims);
                setAuthentication(claims);
            } catch (TokenExpiredException ex) {
                log.warn("만료된 Access token: {}", ex.getMessage());
                request.setAttribute("jwtException", ex);
            } catch (InvalidTokenException ex) {
                log.warn("유효하지 않은 token: {}", ex.getMessage());
                request.setAttribute("jwtException", ex);
            } catch (JwtException ex) {
                log.warn("JWT 처리 실패: {}", ex.getMessage());
                request.setAttribute("jwtException", ex);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void validateTokenType(Claims claims) {
        String typeValue = (String) claims.get("type");
        if (!TokenType.ACCESS.getValue().equals(typeValue)) {
            throw new InvalidTokenException("Access token만 사용 가능합니다.");
        }
        if (!claims.containsKey("userId")) {
            throw new InvalidTokenException("필수 클레임이 누락되었습니다.");
        }
    }

    private void setAuthentication(Claims claims) {
        Long userId = claims.get("userId", Long.class);
        String email = claims.get("email", String.class);
        var auth = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        auth.setDetails(email);
        SecurityContextHolder.getContext().setAuthentication(auth);
        log.debug("JWT 인증 성공: userId={}", userId);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
