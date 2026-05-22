package ssafy.SSAju.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
public class JwtUtil {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_EMAIL = "email";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
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
        return buildToken(userId, email, TOKEN_TYPE_ACCESS, accessTokenExpirationMs);
    }

    public String generateRefreshToken(Long userId) {
        return buildToken(userId, null, TOKEN_TYPE_REFRESH, refreshTokenExpirationMs);
    }

    public record ParsedToken(Long userId, String email) {}

    /**
     * JWT 토큰을 검증하고 userId, email을 한 번의 파싱으로 추출합니다.
     *
     * @param token JWT 문자열
     * @return 유효한 경우 ParsedToken, 유효하지 않은 경우 empty
     */
    public Optional<ParsedToken> validateAndParse(String token) {
        try {
            Claims claims = parseClaims(token);
            Long userId = claims.get(CLAIM_USER_ID, Long.class);
            String email = claims.get(CLAIM_EMAIL, String.class);
            return Optional.of(new ParsedToken(userId, email));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("유효하지 않은 JWT 토큰: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * RefreshToken 만료 시간을 계산합니다.
     *
     * @return 현재 시각으로부터 RefreshToken 유효 기간만큼 더한 Instant (7일)
     */
    public Instant getRefreshTokenExpiration() {
        return Instant.now().plusSeconds(refreshTokenExpirationMs / 1000);
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
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

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
