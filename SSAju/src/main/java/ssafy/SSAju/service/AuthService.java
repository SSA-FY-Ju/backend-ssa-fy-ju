package ssafy.SSAju.service;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.dto.request.LoginRequest;
import ssafy.SSAju.dto.request.SignupRequest;
import ssafy.SSAju.dto.response.AuthTokenPair;
import ssafy.SSAju.dto.response.SignupResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.LoginFailureReason;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.event.LoginAttemptEvent;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.NestedExceptionUtils;
import ssafy.SSAju.exception.AuthException;
import ssafy.SSAju.exception.ConsentRequiredException;
import ssafy.SSAju.exception.DuplicateEmailException;
import ssafy.SSAju.exception.InvalidTokenException;
import ssafy.SSAju.exception.TokenExpiredException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.security.redis.AccessTokenBlacklistService;
import ssafy.SSAju.security.redis.RefreshTokenRedisRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.util.DatabaseConstraints;
import ssafy.SSAju.annotation.AuditLog;
import ssafy.SSAju.util.JwtUtil;
import ssafy.SSAju.util.TokenHashUtil;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 *   <li>RefreshToken은 Redis에 TTL과 함께 저장(회전), AccessToken은 로그아웃/탈퇴 시 Redis 블랙리스트로 즉시 무효화</li>
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
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final AccessTokenBlacklistService blacklistService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ApplicationEventPublisher eventPublisher;
    private final AnalysisResultMaskingService analysisMaskingService;
    private final Clock clock;

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
    @AuditLog
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        checkEmailAvailability(request.email());

        if (!Boolean.TRUE.equals(request.termsAgreed()) || !Boolean.TRUE.equals(request.privacyAgreed())) {
            throw new ConsentRequiredException(ErrorMessageConstants.TERMS_AGREEMENT_REQUIRED.getMessage());
        }

        Instant now = clock.instant();
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
            // checkEmailAvailability()를 먼저 호출했음에도 발생하는 경우는 동시 요청 race condition임.
            // ConstraintViolationException의 제약 이름을 확인하여 이메일 UNIQUE 위반만 DuplicateEmailException으로 변환.
            // FK 위반 등 다른 제약 위반은 그대로 재던져 DataAccessException 핸들러가 처리하도록 함.
            Throwable rootCause = NestedExceptionUtils.getRootCause(e);
            if (rootCause instanceof ConstraintViolationException cve &&
                    cve.getConstraintName() != null &&
                    cve.getConstraintName().contains(DatabaseConstraints.USER_EMAIL_UNIQUE)) {
                throw new DuplicateEmailException("이미 사용 중인 이메일입니다.");
            }
            throw e;
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
     *   <li>RefreshToken 생성 및 Redis 저장 (7일 TTL)</li>
     *   <li>마지막 로그인 시각 업데이트</li>
     *   <li>로그인 시도 이벤트 발행 (비동기 기록)</li>
     * </ol>
     *
     * <p><b>보안 특성:</b>
     * <ul>
     *   <li>이메일 미존재와 비밀번호 오류 시 동일한 에러 메시지 반환</li>
     *   <li>실제 실패 원인(INVALID_EMAIL vs WRONG_PASSWORD)은 이벤트에 기록</li>
     *   <li>RefreshToken은 SHA-256으로 해시하여 Redis 저장(응답은 HttpOnly 쿠키로만 전달 — AuthController)</li>
     * </ul>
     *
     * @param request 로그인 요청 (이메일, 비밀번호)
     * @param clientIp 클라이언트 IP 주소 (로그인 시도 기록용)
     * @return AccessToken, RefreshToken 및 만료 시간 (초) — AuthController가 RefreshToken을 쿠키로 전달
     * @throws AuthException 이메일 미존재 또는 비밀번호 불일치
     */
    @AuditLog
    @Transactional
    public AuthTokenPair login(LoginRequest request, String clientIp) {
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

        AuthTokenPair tokenPair = issueTokenPair(user);

        user.updateLastLoginAt();
        publishLoginEvent(request.email(), true, LoginFailureReason.SUCCESS, clientIp);

        log.info("로그인 성공: userId={}", user.getId());

        return tokenPair;
    }

    /**
     * 사용자 로그아웃을 처리합니다.
     *
     * <p><b>프로세스:</b>
     * <ul>
     *   <li>현재 AccessToken을 블랙리스트에 등록(남은 유효시간만큼 TTL 부여)</li>
     *   <li>현재 RefreshToken을 Redis에서 삭제</li>
     * </ul>
     *
     * @param userId 로그아웃하는 사용자 ID
     * @param refreshTokenValue RefreshToken 값 (쿠키에서 추출, {@link ssafy.SSAju.filter.TokenValidationFilter}가 사전 검증)
     * @param accessTokenValue AccessToken 값 (Authorization 헤더에서 추출)
     */
    @AuditLog
    @Transactional
    public void logout(Long userId, String refreshTokenValue, String accessTokenValue) {
        invalidateTokens(userId, refreshTokenValue, accessTokenValue);
        log.info("로그아웃 완료: userId={}", userId);
    }

    /**
     * 회원 탈퇴를 처리합니다.
     *
     * <p><b>프로세스:</b>
     * <ol>
     *   <li>비밀번호 재확인</li>
     *   <li>만족도 피드백 등 분석 데이터 삭제</li>
     *   <li>User Soft Delete (이름/이메일 마스킹, deleted_at 설정)</li>
     * </ol>
     *
     * <p>탈퇴 시점에 유효했던 Access/RefreshToken 무효화는 이 메서드가 커밋된 후
     * {@link #invalidateSession(Long, String, String)}을 통해 별도로 수행한다(호출부 참고).
     *
     * @param userId   탈퇴 요청 사용자 ID
     * @param password 재확인용 비밀번호
     * @throws UserNotFoundException 사용자를 찾을 수 없는 경우
     * @throws AuthException 비밀번호가 일치하지 않는 경우
     */
    @AuditLog
    @Transactional
    public void deleteUser(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthException("비밀번호가 일치하지 않습니다.");
        }

        analysisMaskingService.maskForUser(user);

        user.softDelete();
        userRepository.save(user);
        log.info("회원 탈퇴 완료: userId={}", userId);
    }

    /**
     * 탈퇴 트랜잭션 커밋 후 호출되어, 탈퇴 시점에 유효했던 Access/RefreshToken을
     * 로그아웃과 동일하게 무효화한다(블랙리스트 등록 + Redis 키 삭제).
     *
     * @param userId 탈퇴한 사용자 ID
     * @param refreshTokenValue RefreshToken 값 (쿠키에 없을 수 있음 — 이 엔드포인트는 {@code TokenValidationFilter} 대상이 아님)
     * @param accessTokenValue AccessToken 값
     */
    public void invalidateSession(Long userId, String refreshTokenValue, String accessTokenValue) {
        invalidateTokens(userId, refreshTokenValue, accessTokenValue);
    }

    /**
     * RefreshToken으로 새로운 AccessToken을 발급하고, RefreshToken을 회전(rotate)합니다.
     *
     * <p><b>사전 조건:</b>
     * {@link ssafy.SSAju.filter.TokenValidationFilter}에서 RefreshToken의 서명/만료/Redis 존재 여부를
     * 이미 검증한 상태입니다. 이 메서드는 방어적으로 재검증합니다.
     *
     * <p><b>프로세스:</b>
     * <ol>
     *   <li>RefreshToken의 jti로 Redis에서 원자적으로 조회+삭제(소비) 후 해시/소유 사용자 확인</li>
     *   <li>새 AccessToken + RefreshToken 생성 및 Redis 저장</li>
     * </ol>
     *
     * <p>조회와 삭제를 하나의 Redis 원자적 연산({@code consume})으로 묶어 처리하므로,
     * 동일한 RefreshToken으로 동시에 여러 갱신 요청이 들어와도 정확히 하나만 성공한다
     * (분리된 조회~삭제 사이의 경쟁 조건으로 토큰이 중복 소비되는 것을 방지).
     *
     * @param refreshTokenValue 쿠키에서 추출한 RefreshToken 원본값
     * @return 새 AccessToken, 새 RefreshToken 및 만료 시간 (초)
     * @throws InvalidTokenException RefreshToken이 유효하지 않은 경우
     */
    @AuditLog
    @Transactional(readOnly = true)
    public AuthTokenPair refreshAccessToken(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new InvalidTokenException("유효하지 않은 리프레시 토큰입니다.");
        }

        Claims claims = jwtUtil.parseAndValidateClaims(refreshTokenValue);
        String jti = claims.get("jti", String.class);

        RefreshTokenRedisRepository.StoredRefreshToken stored =
                refreshTokenRedisRepository.consume(jti, TokenHashUtil.sha256(refreshTokenValue))
                        .orElseThrow(() -> new InvalidTokenException("유효하지 않은 리프레시 토큰입니다."));

        User user = userRepository.findById(stored.userId())
                .orElseThrow(() -> new InvalidTokenException("유효하지 않은 리프레시 토큰입니다."));

        AuthTokenPair tokenPair = issueTokenPair(user);

        log.info("AccessToken/RefreshToken 갱신 완료: userId={}", user.getId());
        return tokenPair;
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
     * 신규 AccessToken + RefreshToken 쌍을 발급하고 RefreshToken을 Redis에 저장한다.
     * 로그인과 토큰 갱신(회전) 양쪽에서 공통으로 사용된다.
     */
    private AuthTokenPair issueTokenPair(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshTokenValue = jwtUtil.generateRefreshToken(user.getId());
        String jti = jwtUtil.extractJti(refreshTokenValue);

        refreshTokenRedisRepository.save(
                jti,
                user.getId(),
                TokenHashUtil.sha256(refreshTokenValue),
                Duration.ofSeconds(jwtUtil.getRefreshTokenExpirationSeconds()));

        return new AuthTokenPair(accessToken, refreshTokenValue, jwtUtil.getAccessTokenExpirationSeconds());
    }

    /**
     * 현재 AccessToken을 블랙리스트에 등록하고, 현재 RefreshToken을 Redis에서 삭제한다.
     * 로그아웃과 탈퇴 후 세션 무효화 양쪽에서 공통으로 사용된다.
     */
    private void invalidateTokens(Long userId, String refreshTokenValue, String accessTokenValue) {
        if (accessTokenValue != null && !accessTokenValue.isBlank()) {
            try {
                Claims accessClaims = jwtUtil.parseAndValidateClaims(accessTokenValue);
                String accessJti = accessClaims.get("jti", String.class);
                Instant accessExpiry = accessClaims.getExpiration().toInstant();
                blacklistService.blacklist(accessJti, accessExpiry);
            } catch (TokenExpiredException | InvalidTokenException ex) {
                // 이미 만료/무효화된 AccessToken은 블랙리스트에 올릴 필요가 없다(어차피 인증에 사용 불가).
                log.debug("로그아웃 시 AccessToken이 이미 유효하지 않음: {}", ex.getMessage());
            }
        }

        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            String refreshJti = jwtUtil.extractJti(refreshTokenValue);
            if (refreshJti != null) {
                refreshTokenRedisRepository.find(refreshJti)
                        .filter(stored -> stored.userId().equals(userId))
                        .ifPresent(stored -> refreshTokenRedisRepository.delete(refreshJti));
            }
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
        eventPublisher.publishEvent(new LoginAttemptEvent(email, success, reason, clientIp, clock.instant()));
    }

}
