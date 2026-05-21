package ssafy.SSAju.controller;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.dto.request.DeleteUserRequest;
import ssafy.SSAju.dto.response.AnalysisDetailResponse;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.MyPageResponse;
import ssafy.SSAju.dto.response.ReanalyzeResponse;
import ssafy.SSAju.service.AuthService;
import ssafy.SSAju.service.UserService;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final UserService userService;
    private final ssafy.SSAju.util.CookieUtil cookieUtil;


    @DeleteMapping("/api/users/me")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @Valid @RequestBody DeleteUserRequest request,
            @AuthenticationPrincipal Long userId,
            HttpServletResponse response) {
        authService.deleteUser(userId, request.password());
        cookieUtil.clearRefreshTokenCookie(response);
        return ResponseEntity.ok(ApiResponse.success(null));
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

    @PostMapping("/api/mypage/reanalyze/{analysisId}")
    public ResponseEntity<ApiResponse<ReanalyzeResponse>> reanalyze(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long analysisId,
            @RequestParam AnalysisType type) {
        ReanalyzeResponse response = userService.reanalyze(userId, analysisId, type);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
