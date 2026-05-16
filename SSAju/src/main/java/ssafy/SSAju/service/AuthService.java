package ssafy.SSAju.service;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.dto.request.LoginRequest;
import ssafy.SSAju.dto.request.SignupRequest;
import ssafy.SSAju.dto.response.AuthTokenResponse;
import ssafy.SSAju.dto.response.SignupResponse;
import ssafy.SSAju.entity.RefreshToken;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.LoginFailureReason;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.event.LoginAttemptEvent;
import ssafy.SSAju.exception.AuthException;
import ssafy.SSAju.exception.DuplicateEmailException;
import ssafy.SSAju.repository.RefreshTokenRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.util.CookieUtil;
import ssafy.SSAju.util.JwtUtil;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("이미 사용 중인 이메일입니다.");
        }

        if (!Boolean.TRUE.equals(request.termsAgreed()) || !Boolean.TRUE.equals(request.privacyAgreed())) {
            throw new AuthException("이용약관 및 개인정보 수집에 동의해야 합니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(now)
                .privacyAgreedAt(now)
                .build();

        userRepository.save(user);
        log.info("회원가입 완료: userId={}", user.getId());

        return new SignupResponse("회원가입 완료. 로그인해주세요.", "/login");
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request, String clientIp, HttpServletResponse response) {
        Optional<User> userOpt = userRepository.findByEmail(request.email());

        if (userOpt.isEmpty()) {
            publishLoginEvent(request.email(), false, LoginFailureReason.INVALID_EMAIL, clientIp);
            throw new AuthException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        User user = userOpt.get();

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            publishLoginEvent(request.email(), false, LoginFailureReason.WRONG_PASSWORD, clientIp);
            throw new AuthException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String refreshTokenValue = jwtUtil.generateRefreshToken(user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenValue)
                .expiresAt(jwtUtil.getRefreshTokenExpiration())
                .build();
        refreshTokenRepository.save(refreshToken);

        user.updateLastLoginAt();
        cookieUtil.setRefreshTokenCookie(response, refreshTokenValue);
        publishLoginEvent(request.email(), true, LoginFailureReason.SUCCESS, clientIp);

        log.info("로그인 성공: userId={}", user.getId());

        return new AuthTokenResponse(accessToken, jwtUtil.getAccessTokenExpirationSeconds());
    }

    @Transactional
    public void logout(Long userId, String refreshTokenValue, HttpServletResponse response) {
        if (refreshTokenValue != null) {
            refreshTokenRepository.findByTokenHash(refreshTokenValue)
                    .filter(rt -> rt.getUser().getId().equals(userId))
                    .ifPresent(RefreshToken::revoke);
        }

        cookieUtil.clearRefreshTokenCookie(response);
        log.info("로그아웃 완료: userId={}", userId);
    }

    private void publishLoginEvent(String email, boolean success, LoginFailureReason reason, String clientIp) {
        eventPublisher.publishEvent(new LoginAttemptEvent(email, success, reason, clientIp, LocalDateTime.now()));
    }
}
