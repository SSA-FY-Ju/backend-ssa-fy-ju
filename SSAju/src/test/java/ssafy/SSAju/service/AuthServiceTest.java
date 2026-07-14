package ssafy.SSAju.service;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import ssafy.SSAju.dto.response.AuthTokenPair;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.InvalidTokenException;
import ssafy.SSAju.security.redis.AccessTokenBlacklistService;
import ssafy.SSAju.security.redis.RefreshTokenRedisRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.util.JwtUtil;
import ssafy.SSAju.util.TokenHashUtil;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRedisRepository refreshTokenRedisRepository;
    @Mock private AccessTokenBlacklistService blacklistService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AnalysisResultMaskingService analysisResultMaskingService;

    private Clock clock;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        Field clockField = AuthService.class.getDeclaredField("clock");
        clockField.setAccessible(true);
        clockField.set(authService, clock);

        testUser = User.builder()
                .email("test@test.com")
                .passwordHash("encoded-password")
                .name("테스트유저")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(Instant.now())
                .privacyAgreedAt(Instant.now())
                .build();

        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(testUser, 1L);
    }

    // ===== refreshAccessToken 테스트 =====

    @Test
    void refreshAccessToken_유효한토큰_새AccessToken및RefreshToken발급() {
        // Given
        String tokenValue = "valid-refresh-token";
        Claims claims = mock(Claims.class);
        given(claims.get("jti", String.class)).willReturn("jti-1");
        given(jwtUtil.parseAndValidateClaims(tokenValue)).willReturn(claims);
        given(refreshTokenRedisRepository.consume("jti-1", TokenHashUtil.sha256(tokenValue))).willReturn(
                Optional.of(new RefreshTokenRedisRepository.StoredRefreshToken(1L, TokenHashUtil.sha256(tokenValue))));
        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(jwtUtil.generateAccessToken(1L, "test@test.com", "USER")).willReturn("new-access-token");
        given(jwtUtil.generateRefreshToken(1L)).willReturn("new-refresh-token");
        given(jwtUtil.extractJti("new-refresh-token")).willReturn("jti-2");
        given(jwtUtil.getRefreshTokenExpirationSeconds()).willReturn(604800L);
        given(jwtUtil.getAccessTokenExpirationSeconds()).willReturn(3600L);

        // When
        AuthTokenPair result = authService.refreshAccessToken(tokenValue);

        // Then
        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshTokenValue()).isEqualTo("new-refresh-token");
        assertThat(result.expiresIn()).isEqualTo(3600L);
        verify(refreshTokenRedisRepository).save(
                eq("jti-2"), eq(1L),
                eq(TokenHashUtil.sha256("new-refresh-token")),
                eq(Duration.ofSeconds(604800L)));
    }

    @Test
    void refreshAccessToken_Redis에없는토큰_InvalidTokenException발생() {
        // Given
        String tokenValue = "unknown-token";
        Claims claims = mock(Claims.class);
        given(claims.get("jti", String.class)).willReturn("missing-jti");
        given(jwtUtil.parseAndValidateClaims(tokenValue)).willReturn(claims);
        given(refreshTokenRedisRepository.consume("missing-jti", TokenHashUtil.sha256(tokenValue)))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.refreshAccessToken(tokenValue))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshAccessToken_토큰해시불일치_InvalidTokenException발생() {
        // Given: consume()은 해시가 일치하지 않아도 원자적으로 이미 삭제(소비)하고 empty를 반환한다
        // (재사용/탈취 시도된 토큰도 이후 정상 토큰으로 재사용할 수 없도록 방어).
        String tokenValue = "tampered-token";
        Claims claims = mock(Claims.class);
        given(claims.get("jti", String.class)).willReturn("jti-1");
        given(jwtUtil.parseAndValidateClaims(tokenValue)).willReturn(claims);
        given(refreshTokenRedisRepository.consume("jti-1", TokenHashUtil.sha256(tokenValue)))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.refreshAccessToken(tokenValue))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshAccessToken_빈문자열_InvalidTokenException발생() {
        assertThatThrownBy(() -> authService.refreshAccessToken(""))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ===== logout 테스트 =====

    @Test
    void logout_유효한토큰_블랙리스트등록및RefreshToken삭제() {
        // Given
        String accessToken = "valid-access-token";
        String refreshToken = "valid-refresh-token";

        Claims accessClaims = mock(Claims.class);
        given(accessClaims.get("jti", String.class)).willReturn("access-jti");
        given(accessClaims.getExpiration()).willReturn(Date.from(clock.instant().plusSeconds(3600)));
        given(jwtUtil.parseAndValidateClaims(accessToken)).willReturn(accessClaims);

        given(jwtUtil.extractJti(refreshToken)).willReturn("refresh-jti");
        given(refreshTokenRedisRepository.find("refresh-jti")).willReturn(
                Optional.of(new RefreshTokenRedisRepository.StoredRefreshToken(1L, "hash")));

        // When
        authService.logout(1L, refreshToken, accessToken);

        // Then
        verify(blacklistService).blacklist(eq("access-jti"), any(Instant.class));
        verify(refreshTokenRedisRepository).delete("refresh-jti");
    }

    @Test
    void logout_토큰없음_예외없이정상종료() {
        // When & Then: 예외 없이 정상 종료
        authService.logout(1L, null, null);
        verify(blacklistService, never()).blacklist(anyString(), any(Instant.class));
    }

    @Test
    void logout_다른사용자소유RefreshToken_삭제되지않음() {
        // Given: RefreshToken의 소유자는 2L이지만, 요청 userId는 1L
        String refreshToken = "other-user-token";
        given(jwtUtil.extractJti(refreshToken)).willReturn("other-jti");
        given(refreshTokenRedisRepository.find("other-jti")).willReturn(
                Optional.of(new RefreshTokenRedisRepository.StoredRefreshToken(2L, "hash")));

        // When
        authService.logout(1L, refreshToken, null);

        // Then
        verify(refreshTokenRedisRepository, never()).delete(anyString());
    }
}
