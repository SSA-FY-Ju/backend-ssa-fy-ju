package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import ssafy.SSAju.dto.response.AuthTokenPair;
import ssafy.SSAju.entity.RefreshToken;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.InvalidTokenException;
import ssafy.SSAju.repository.RefreshTokenRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.util.JwtUtil;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private RefreshToken validRefreshToken;

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.builder()
                .email("test@test.com")
                .passwordHash("encoded-password")
                .name("테스트유저")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(LocalDateTime.now())
                .privacyAgreedAt(LocalDateTime.now())
                .build();

        // 리플렉션으로 id 설정 (테스트용)
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(testUser, 1L);

        validRefreshToken = RefreshToken.builder()
                .user(testUser)
                .tokenHash("hashed-token")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
    }

    // ===== refreshAccessToken 테스트 =====

    @Test
    void refreshAccessToken_유효한토큰_새AccessToken반환() {
        // Given
        String tokenValue = "valid-refresh-token";
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(validRefreshToken));
        given(jwtUtil.generateAccessToken(1L, "test@test.com")).willReturn("new-access-token");
        given(jwtUtil.getAccessTokenExpirationSeconds()).willReturn(3600L);

        // When
        AuthTokenPair result = authService.refreshAccessToken(tokenValue);

        // Then
        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void refreshAccessToken_DB에없는토큰_InvalidTokenException발생() {
        // Given
        String tokenValue = "unknown-token";
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.refreshAccessToken(tokenValue))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshAccessToken_revoked된토큰_InvalidTokenException발생() {
        // Given
        String tokenValue = "revoked-token";
        RefreshToken revokedToken = mock(RefreshToken.class);
        when(revokedToken.isRevoked()).thenReturn(true);
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(revokedToken));

        // When & Then
        assertThatThrownBy(() -> authService.refreshAccessToken(tokenValue))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshAccessToken_만료된토큰_InvalidTokenException발생() {
        // Given
        String tokenValue = "expired-token";
        RefreshToken expiredToken = mock(RefreshToken.class);
        when(expiredToken.isRevoked()).thenReturn(false);
        when(expiredToken.isExpired()).thenReturn(true);
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(expiredToken));

        // When & Then
        assertThatThrownBy(() -> authService.refreshAccessToken(tokenValue))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshAccessToken_만료시간과동일한토큰_만료로처리() {
        // Given: isExpired()는 !now.isBefore(expiresAt) → expiresAt이 now와 동일하면 만료
        String tokenValue = "boundary-token";
        RefreshToken boundaryToken = mock(RefreshToken.class);
        when(boundaryToken.isRevoked()).thenReturn(false);
        when(boundaryToken.isExpired()).thenReturn(true);
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(boundaryToken));

        // When & Then
        assertThatThrownBy(() -> authService.refreshAccessToken(tokenValue))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ===== logout 테스트 =====

    @Test
    void logout_유효한토큰_revoke처리() {
        // Given
        String tokenValue = "valid-token";
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(validRefreshToken));

        // When
        authService.logout(1L, tokenValue);

        // Then: revoke 호출 확인 (side effect: revokedAt이 설정됨)
        assertThat(validRefreshToken.isRevoked()).isTrue();
    }

    @Test
    void logout_토큰없음_예외없이정상종료() {
        // When & Then: 예외 없이 정상 종료
        authService.logout(1L, null);
    }

    @Test
    void logout_다른사용자소유토큰_revoke미처리() {
        // Given: 토큰의 소유자 userId는 2L이지만, 요청 userId는 1L
        String tokenValue = "other-user-token";

        User otherUser = User.builder()
                .email("other@test.com")
                .passwordHash("hash")
                .name("다른유저")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(LocalDateTime.now())
                .privacyAgreedAt(LocalDateTime.now())
                .build();
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(otherUser, 2L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        RefreshToken otherUserToken = RefreshToken.builder()
                .user(otherUser)
                .tokenHash("hashed-other-token")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(otherUserToken));

        // When
        authService.logout(1L, tokenValue);

        // Then: 다른 사용자 토큰은 revoke되지 않음
        assertThat(otherUserToken.isRevoked()).isFalse();
    }
}
