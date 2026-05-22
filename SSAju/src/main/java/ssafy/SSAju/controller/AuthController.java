package ssafy.SSAju.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.SSAju.dto.request.EmailCheckRequest;
import ssafy.SSAju.dto.request.LoginRequest;
import ssafy.SSAju.dto.request.SignupRequest;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.AuthTokenPair;
import ssafy.SSAju.dto.response.AuthTokenResponse;
import ssafy.SSAju.dto.response.SignupResponse;
import ssafy.SSAju.exception.AuthException;
import ssafy.SSAju.service.AuthService;
import ssafy.SSAju.util.ClientIpUtil;

/**
 * 인증 관련 REST 컨트롤러.
 *
 * 회원가입, 로그인, 로그아웃 엔드포인트를 제공합니다.
 * 모든 요청/응답은 {@link ApiResponse} 형태로 표준화됩니다.
 *
 * @author SSAju Team
 * @see AuthService
 * @see ssafy.SSAju.filter.JwtAuthenticationFilter
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 회원가입 요청을 처리합니다.
     *
     * <p><b>프로세스:</b>
     * <ul>
     *   <li>이메일 중복 확인</li>
     *   <li>약관 동의 여부 검증</li>
     *   <li>비밀번호 BCrypt 암호화</li>
     *   <li>사용자 정보 저장</li>
     * </ul>
     *
     * @param request 회원가입 요청 정보
     * @return 201 Created, 회원가입 완료 메시지 및 리다이렉트 URL
     * @throws DuplicateEmailException 이메일이 이미 등록되었을 경우
     * @throws AuthException 약관 미동의 또는 기타 인증 오류
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * 로그인 요청을 처리합니다.
     *
     * <p><b>프로세스:</b>
     * <ul>
     *   <li>이메일로 사용자 조회</li>
     *   <li>비밀번호 일치 확인</li>
     *   <li>AccessToken (1시간) 및 RefreshToken (7일) 생성</li>
     *   <li>AccessToken은 Authorization 헤더, RefreshToken은 Refresh-Token 헤더로 전달</li>
     *   <li>로그인 시도 이벤트 발행 (비동기)</li>
     * </ul>
     *
     * <p><b>보안 특성:</b>
     * <ul>
     *   <li>이메일 미존재와 비밀번호 오류 시 동일한 에러 메시지 반환 (User Enumeration 방지)</li>
     *   <li>실제 실패 원인은 LoginAttempt 기록에 저장 (감사 추적)</li>
     *   <li>클라이언트 IP는 프록시 헤더를 통해 추출</li>
     * </ul>
     *
     * @param request 로그인 요청 정보
     * @param httpRequest HTTP 요청 (클라이언트 IP 추출용)
     * @param httpResponse HTTP 응답 (RefreshToken 쿠키 설정용)
     * @return 200 OK, AccessToken 및 만료 시간
     * @throws AuthException 이메일 미존재 또는 비밀번호 불일치
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String clientIp = ClientIpUtil.getClientIp(httpRequest);
        AuthTokenPair tokenPair = authService.login(request, clientIp);
        httpResponse.setHeader("Authorization", "Bearer " + tokenPair.accessToken());
        httpResponse.setHeader("Refresh-Token", tokenPair.refreshTokenValue());
        return ResponseEntity.ok(ApiResponse.success(
                new AuthTokenResponse(tokenPair.expiresIn())));
    }

    /**
     * 로그아웃 요청을 처리합니다.
     *
     * <p><b>프로세스:</b>
     * <ul>
     *   <li>SecurityContext에서 현재 사용자 ID 추출</li>
     *   <li>Refresh-Token 요청 헤더에서 토큰값 추출</li>
     *   <li>RefreshToken을 revoked 상태로 표시</li>
     * </ul>
     *
     * @param request HTTP 요청 (RefreshToken 쿠키 추출용)
     * @param response HTTP 응답 (쿠키 제거용)
     * @return 200 OK
     * @throws AuthException 인증 정보를 찾을 수 없는 경우
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        Long userId = getCurrentUserId();
        String refreshToken = request.getHeader("Refresh-Token");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthException("리프레시 토큰이 없습니다.");
        }
        authService.logout(userId, refreshToken);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * RefreshToken으로 새로운 AccessToken을 발급합니다.
     *
     * <p>{@link ssafy.SSAju.filter.TokenValidationFilter}에서 RefreshToken의 유효성을
     * 이미 검증한 후 이 엔드포인트에 도달합니다.
     *
     * @param request HTTP 요청 (Refresh-Token 헤더 추출용)
     * @return 200 OK, 새 AccessToken 및 만료 시간
     * @throws ssafy.SSAju.exception.InvalidTokenException RefreshToken이 유효하지 않은 경우
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refresh(HttpServletRequest request,
                                                                   HttpServletResponse response) {
        String refreshToken = request.getHeader("Refresh-Token");
        AuthTokenPair tokenPair = authService.refreshAccessToken(refreshToken);
        response.setHeader("Authorization", "Bearer " + tokenPair.accessToken());
        response.setHeader("Refresh-Token", tokenPair.refreshTokenValue());
        return ResponseEntity.ok(ApiResponse.success(new AuthTokenResponse(tokenPair.expiresIn())));
    }

    /**
     * 이메일 중복 여부를 사전 확인합니다.
     *
     * <p>회원가입 폼에서 실시간으로 이메일 사용 가능 여부를 확인하는 용도입니다.
     * 중복이 없으면 200 OK, 중복이 있으면 409 Conflict를 반환합니다.
     *
     * @param request 이메일 확인 요청
     * @return 200 OK (사용 가능한 이메일)
     * @throws DuplicateEmailException 이미 등록된 이메일인 경우
     */
    @PostMapping("/check-email")
    public ResponseEntity<ApiResponse<Void>> checkEmail(
            @Valid @RequestBody EmailCheckRequest request) {
        authService.checkEmailAvailability(request.email());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            throw new AuthException("인증 정보를 찾을 수 없습니다.");
        }
        return (Long) auth.getPrincipal();
    }
}
