package ssafy.SSAju.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import ssafy.SSAju.exception.InvalidTokenException;
import ssafy.SSAju.exception.TokenExpiredException;
import ssafy.SSAju.util.TokenType;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TYPE = "type";

    private final SecretKey secretKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(Long userId, String email) {
        return buildToken(userId, email, TokenType.ACCESS.getValue(), accessTokenExpirationMs);
    }

    public String generateRefreshToken(Long userId) {
        return buildToken(userId, null, TokenType.REFRESH.getValue(), refreshTokenExpirationMs);
    }

    /**
     * JWT 토큰을 파싱하고 Claims를 반환합니다. 만료/위조 여부를 구체적인 예외로 구분합니다.
     */
    public Claims parseAndValidateClaims(String token) {
        if (!StringUtils.hasText(token)) {
            throw new InvalidTokenException("토큰이 제공되지 않았습니다.");
        }
        try {
            return parseClaims(token);
        } catch (ExpiredJwtException ex) {
            throw new TokenExpiredException("Access token이 만료되었습니다. 새로 발급해 주세요.");
        } catch (SignatureException | MalformedJwtException ex) {
            throw new InvalidTokenException("위조되거나 손상된 토큰입니다.");
        } catch (UnsupportedJwtException ex) {
            throw new InvalidTokenException("지원하지 않는 토큰 형식입니다.");
        } catch (JwtException ex) {
            throw new InvalidTokenException("유효하지 않은 토큰입니다.");
        }
    }

    /**
     * RefreshToken 만료 시간을 계산합니다.
     *
     * @return 현재 시각으로부터 RefreshToken 유효 기간만큼 더한 Instant (7일)
     */
    public Instant getRefreshTokenExpiration() {
        return Instant.now().plus(Duration.ofMillis(refreshTokenExpirationMs));
    }

    /**
     * AccessToken 만료 시간을 초 단위로 반환합니다.
     *
     * <p>클라이언트에서 토큰 유효 시간 내에 토큰 갱신이 필요한지 판단하거나,
     * 프론트엔드 타이머 구현 시 사용됩니다.
     *
     * @return AccessToken 만료 시간 (초 단위), 일반적으로 3600초(1시간)
     */
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }

    private String buildToken(Long userId, String email, String type, long expirationMs) {
        // Instant 기반으로 생성하여 타임존 독립성 보장 (M-7).
        Instant nowInstant = Instant.now();
        Date now = Date.from(nowInstant);
        Date expiry = Date.from(nowInstant.plusMillis(expirationMs));

        var builder = Jwts.builder()
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey);

        if (email != null) {
            builder.claim(CLAIM_EMAIL, email);
        }

        return builder.compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
