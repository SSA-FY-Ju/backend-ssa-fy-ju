package ssafy.SSAju.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.dto.request.DefaultProfileRequest;
import ssafy.SSAju.dto.request.DeleteUserRequest;
import ssafy.SSAju.dto.response.AnalysisDetailResponse;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.DefaultProfileResponse;
import ssafy.SSAju.dto.response.MyPageResponse;
import ssafy.SSAju.service.AuthService;
import ssafy.SSAju.service.DefaultProfileService;
import ssafy.SSAju.service.UserService;
import ssafy.SSAju.util.BearerTokenUtil;
import ssafy.SSAju.util.CookieUtil;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final UserService userService;
    private final DefaultProfileService defaultProfileService;
    private final CookieUtil cookieUtil;

    @DeleteMapping("/api/users/me")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @Valid @RequestBody DeleteUserRequest request,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String accessToken = BearerTokenUtil.extractBearerToken(httpRequest);
        String refreshToken = CookieUtil.getRefreshTokenFromCookie(httpRequest);
        authService.deleteUser(userId, request.password());
        authService.invalidateSession(userId, refreshToken, accessToken);
        cookieUtil.clearRefreshTokenCookie(response);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 기본 출생정보 조회. 설정하지 않았으면 {@code data}가 null이며, 클라이언트는 직접 입력을 받는다.
     */
    @GetMapping("/api/users/me/default-profile")
    public ResponseEntity<ApiResponse<DefaultProfileResponse>> getDefaultProfile(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(defaultProfileService.getDefaultProfile(userId)));
    }

    /**
     * 기본 출생정보 저장·변경. 공유 프로필을 수정하지 않고 사용자의 참조만 교체한다.
     * 외부 호출·만세력 생성·분석 저장·쿼터 차감이 모두 없다.
     */
    @PutMapping("/api/users/me/default-profile")
    public ResponseEntity<ApiResponse<DefaultProfileResponse>> changeDefaultProfile(
            @Valid @RequestBody DefaultProfileRequest request,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                defaultProfileService.changeDefaultProfile(userId, request)));
    }

    @GetMapping("/api/mypage")
    public ResponseEntity<ApiResponse<MyPageResponse>> getMyPage(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) AnalysisType type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size) {
        MyPageResponse response = userService.getMyPage(userId, type, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/api/mypage/analyses/{analysisId}")
    public ResponseEntity<ApiResponse<AnalysisDetailResponse>> getAnalysisDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long analysisId,
            @RequestParam AnalysisType type) {
        AnalysisDetailResponse response = userService.getAnalysisDetail(userId, analysisId, type);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

}
