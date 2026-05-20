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
import org.springframework.dao.DataIntegrityViolationException;
import ssafy.SSAju.exception.AuthException;
import ssafy.SSAju.exception.DuplicateEmailException;
import ssafy.SSAju.exception.InvalidTokenException;
import ssafy.SSAju.repository.RefreshTokenRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.util.CookieUtil;
import ssafy.SSAju.util.JwtUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 인증 비즈니스 로직 서비스.
 *
 * 회원가입, 로그인, 로그아웃의 핵심 비즈니스 로직을 담당합니다.
 * 사용자 정보 검증, 토큰 생성, 쿠키 설정, 이벤트 발행 등을 처리합니다.
 *
 * <p><b>주요 특성:</b>
 * <ul>
 *   <li>BCrypt 기반 비밀번호 암호화</li>
 *   <li>AccessToken (1시간) + RefreshToken (7일) 투트랙 인증</li>
 *   <li>이벤트 기반 비동기 로그인 시도 기록</li>
 *   <li>User Enumeration 공격 방지 (동일 에러 메시지)</li>
 * </ul>
 *
 * @author SSAju Team
 * @see AuthController
 * @see LoginAttemptEventListener
 */
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

    /**
     * 사용자 회원가입을 처리합니다.
     *
     * <p><b>검증 단계:</b>
     * <ul>
     *   <li>이메일 중복 확인</li>
     *   <li>이용약관 및 개인정보 수집 동의 여부 확인</li>
     * </ul>
     *
     * <p><b>저장되는 정보:</b>
     * <ul>
     *   <li>이메일 (unique)</li>
     *   <li>비밀번호 (BCrypt 암호화)</li>
     *   <li>사용자 이름</li>
     *   <li>약관 동의 시각</li>
     *   <li>역할: USER, 상태: ACTIVE</li>
     * </ul>
     *
     * @param request 회원가입 요청
     * @return 회원가입 완료 메시지 및 리다이렉트 URL
     * @throws DuplicateEmailException 이메일이 이미 등록되었을 경우
     * @throws AuthException 약관 미동의인 경우
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        checkEmailAvailability(request.email());

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

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEmailException("이미 사용 중인 이메일입니다.");
        }
        log.info("회원가입 완료: userId={}", user.getId());

        return new SignupResponse("회원가입 완료. 로그인해주세요.", "/login");
    }

    /**
     * 사용자 로그인을 처리합니다.
     *
     * <p><b>프로세스:</b>
     * <ol>
     *   <li>이메일로 사용자 조회</li>
     *   <li>비밀번호 일치 확인</li>
     *   <li>AccessToken 생성 (1시간 유효)</li>
     *   <li>RefreshToken 생성 및 DB 저장 (7일 유효)</li>
     *   <li>RefreshToken을 HttpOnly 쿠키에 설정</li>
     *   <li>마지막 로그인 시각 업데이트</li>
     *   <li>로그인 시도 이벤트 발행 (비동기 기록)</li>
     * </ol>
     *
     * <p><b>보안 특성:</b>
     * <ul>
     *   <li>이메일 미존재와 비밀번호 오류 시 동일한 에러 메시지 반환</li>
     *   <li>실제 실패 원인(INVALID_EMAIL vs WRONG_PASSWORD)은 이벤트에 기록</li>
     *   <li>RefreshToken은 SHA-256으로 해시하여 DB 저장 (쿠키에는 원본값 유지)</li>
     * </ul>
     *
     * @param request 로그인 요청 (이메일, 비밀번호)
     * @param clientIp 클라이언트 IP 주소 (로그인 시도 기록용)
     * @param response HTTP 응답 (RefreshToken 쿠키 설정용)
     * @return AccessToken 및 만료 시간 (초)
     * @throws AuthException 이메일 미존재 또는 비밀번호 불일치
     */
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
                .tokenHash(hashToken(refreshTokenValue))
                .expiresAt(jwtUtil.getRefreshTokenExpiration())
                .build();
        refreshTokenRepository.save(refreshToken);

        user.updateLastLoginAt();
        cookieUtil.setRefreshTokenCookie(response, refreshTokenValue);
        publishLoginEvent(request.email(), true, LoginFailureReason.SUCCESS, clientIp);

        log.info("로그인 성공: userId={}", user.getId());

        return new AuthTokenResponse(accessToken, jwtUtil.getAccessTokenExpirationSeconds());
    }

    /**
     * 사용자 로그아웃을 처리합니다.
     *
     * <p><b>프로세스:</b>
     * <ul>
     *   <li>RefreshToken을 revoked 상태로 표시</li>
     *   <li>RefreshToken 쿠키 제거</li>
     * </ul>
     *
     * <p><b>동시성 안전:</b>
     * RefreshToken이 존재하고 현재 사용자 소유인 경우에만 revoke합니다.
     * 동시 로그아웃 시도 시에도 안전합니다.
     *
     * @param userId 로그아웃하는 사용자 ID
     * @param refreshTokenValue RefreshToken 값 (쿠키에서 추출)
     * @param response HTTP 응답 (쿠키 제거용)
     */
    @Transactional
    public void logout(Long userId, String refreshTokenValue, HttpServletResponse response) {
        if (refreshTokenValue != null) {
            refreshTokenRepository.findByTokenHash(hashToken(refreshTokenValue))
                    .filter(rt -> rt.getUser().getId().equals(userId))
                    .ifPresent(RefreshToken::revoke);
        }

        cookieUtil.clearRefreshTokenCookie(response);
        log.info("로그아웃 완료: userId={}", userId);
    }

    /**
     * RefreshToken으로 새로운 AccessToken을 발급합니다.
     *
     * <p><b>사전 조건:</b>
     * {@link ssafy.SSAju.filter.TokenValidationFilter}에서 RefreshToken의 존재, revoked 여부,
     * 만료 여부를 이미 검증한 상태입니다. 이 메서드는 방어적으로 재검증합니다.
     *
     * <p><b>프로세스:</b>
     * <ol>
     *   <li>RefreshToken 해시 계산 후 DB 조회</li>
     *   <li>revoked/expired 상태 재검증</li>
     *   <li>사용자 정보 기반 새 AccessToken 생성</li>
     * </ol>
     *
     * @param refreshTokenValue 쿠키에서 추출한 RefreshToken 원본값
     * @return 새 AccessToken 및 만료 시간 (초)
     * @throws InvalidTokenException RefreshToken이 유효하지 않은 경우
     */
    @Transactional(readOnly = true)
    public AuthTokenResponse refreshAccessToken(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new InvalidTokenException("유효하지 않은 리프레시 토큰입니다.");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashToken(refreshTokenValue))
                .orElseThrow(() -> new InvalidTokenException("유효하지 않은 리프레시 토큰입니다."));

        if (refreshToken.isRevoked() || refreshToken.isExpired()) {
            throw new InvalidTokenException("유효하지 않은 리프레시 토큰입니다.");
        }

        User user = refreshToken.getUser();
        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());

        log.info("AccessToken 갱신 완료: userId={}", user.getId());
        return new AuthTokenResponse(newAccessToken, jwtUtil.getAccessTokenExpirationSeconds());
    }

    /**
     * 이메일 사용 가능 여부를 확인합니다.
     *
     * <p>회원가입 전 실시간 이메일 중복 확인 API(/api/auth/check-email)에서 사용됩니다.
     * 중복이 없으면 정상 종료, 중복이 있으면 DuplicateEmailException을 던집니다.
     *
     * @param email 확인할 이메일 주소
     * @throws DuplicateEmailException 이미 등록된 이메일인 경우
     */
    public void checkEmailAvailability(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("이미 사용 중인 이메일입니다.");
        }
    }

    /**
     * 로그인 시도 이벤트를 발행합니다.
     *
     * <p>이 메서드는 {@link LoginAttemptEventListener}에 의해 비동기로 처리됩니다.
     * 메인 로그인 로직을 방해하지 않으면서 로그인 시도 기록을 데이터베이스에 저장합니다.
     *
     * @param email 로그인 시도한 이메일 주소
     * @param success 로그인 성공 여부
     * @param reason 실패 사유 (또는 SUCCESS)
     * @param clientIp 클라이언트 IP 주소
     *
     * @see LoginAttemptEvent
     * @see LoginAttemptEventListener#onLoginAttempt
     */
    private void publishLoginEvent(String email, boolean success, LoginFailureReason reason, String clientIp) {
        eventPublisher.publishEvent(new LoginAttemptEvent(email, success, reason, clientIp, LocalDateTime.now()));
    }

    /**
     * 토큰 값을 SHA-256으로 해시합니다.
     *
     * <p>RefreshToken은 DB에 원본값이 아닌 해시값으로 저장됩니다.
     * DB 유출 시 원본 토큰이 노출되는 것을 방지하기 위한 보안 조치입니다.
     *
     * @param token 해시할 토큰 원본값
     * @return SHA-256 해시 (16진수 문자열)
     */
    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
