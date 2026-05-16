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
import java.time.LocalDateTime;
import java.util.Date;

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

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("유효하지 않은 JWT 토큰: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        return parseClaims(token).get(CLAIM_USER_ID, Long.class);
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).get(CLAIM_EMAIL, String.class);
    }

    public LocalDateTime getRefreshTokenExpiration() {
        return LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000);
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
