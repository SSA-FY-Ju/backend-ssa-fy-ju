package ssafy.SSAju.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import ssafy.SSAju.exception.InvalidTokenException;
import ssafy.SSAju.exception.TokenExpiredException;
import ssafy.SSAju.security.redis.AccessTokenBlacklistService;
import ssafy.SSAju.util.JwtUtil;
import ssafy.SSAju.util.TokenType;

import java.io.IOException;
import java.util.List;

/**
 * 사용자/관리자 Access Token 검증 필터의 공통 로직(토큰 파싱→서명/만료 검증→
 * 블랙리스트 확인→{@link SecurityContextHolder} 설정)을 템플릿 메서드로 추출한 추상 필터.
 *
 * <p>토큰을 어디서 추출할지({@code Authorization} 헤더 vs 쿠키)와 검증 실패 시의
 * 부가 동작(쿠키 제거 등)은 하위 클래스가 정의한다.
 */
public abstract class AbstractJwtValidationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AccessTokenBlacklistService blacklistService;

    protected AbstractJwtValidationFilter(JwtUtil jwtUtil, AccessTokenBlacklistService blacklistService) {
        this.jwtUtil = jwtUtil;
        this.blacklistService = blacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            try {
                Claims claims = jwtUtil.parseAndValidateClaims(token);
                validateAccessTokenType(claims);
                checkNotBlacklisted(claims);
                setAuthentication(claims);
            } catch (TokenExpiredException | InvalidTokenException ex) {
                onValidationFailure(request, response, ex);
            } catch (JwtException ex) {
                onValidationFailure(request, response, ex);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 요청에서 Access Token 원본 문자열을 추출합니다. 토큰이 없으면 {@code null}을 반환합니다.
     */
    protected abstract String extractToken(HttpServletRequest request);

    /**
     * 토큰 검증 실패 시 호출됩니다. 기본 동작은 이후 {@code JwtExceptionFilter}가 처리할 수 있도록
     * 예외를 요청 속성에 담아두는 것뿐이며, 하위 클래스는 로깅/쿠키 제거 등 부가 동작을 추가할 수 있습니다.
     */
    protected void onValidationFailure(HttpServletRequest request, HttpServletResponse response, RuntimeException ex) {
        request.setAttribute("jwtException", ex);
    }

    private void validateAccessTokenType(Claims claims) {
        String typeValue = (String) claims.get("type");
        if (!TokenType.ACCESS.getValue().equals(typeValue)) {
            throw new InvalidTokenException("Access token만 사용 가능합니다.");
        }
        if (!claims.containsKey("userId")) {
            throw new InvalidTokenException("필수 클레임이 누락되었습니다.");
        }
    }

    private void checkNotBlacklisted(Claims claims) {
        String jti = claims.get("jti", String.class);
        if (blacklistService.isBlacklisted(jti)) {
            throw new InvalidTokenException("로그아웃되었거나 폐기된 토큰입니다.");
        }
    }

    private void setAuthentication(Claims claims) {
        Long userId = claims.get("userId", Long.class);
        String email = claims.get("email", String.class);
        // role 클레임이 없으면 USER로 폴백 (구버전 토큰 호환)
        String role = claims.get("role", String.class);
        String authority = (role != null) ? "ROLE_" + role : "ROLE_USER";
        var auth = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority(authority))
        );
        auth.setDetails(email);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
