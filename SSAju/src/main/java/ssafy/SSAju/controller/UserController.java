package ssafy.SSAju.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.SSAju.dto.request.DeleteUserRequest;
import ssafy.SSAju.dto.response.AnalysisDetailResponse;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.MyPageResponse;
import ssafy.SSAju.dto.response.ReanalyzeResponse;
import ssafy.SSAju.exception.AuthException;
import ssafy.SSAju.service.AuthService;
import ssafy.SSAju.service.UserService;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final UserService userService;

    @DeleteMapping("/api/users/me")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @Valid @RequestBody DeleteUserRequest request,
            HttpServletResponse response) {
        Long userId = getCurrentUserId();
        authService.deleteUser(userId, request.password(), response);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/api/mypage")
    public ResponseEntity<ApiResponse<MyPageResponse>> getMyPage(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = getCurrentUserId();
        MyPageResponse response = userService.getMyPage(userId, type, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/api/mypage/analyses/{analysisId}")
    public ResponseEntity<ApiResponse<AnalysisDetailResponse>> getAnalysisDetail(
            @PathVariable Long analysisId,
            @RequestParam String type) {
        Long userId = getCurrentUserId();
        AnalysisDetailResponse response = userService.getAnalysisDetail(userId, analysisId, type);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/api/mypage/reanalyze/{analysisId}")
    public ResponseEntity<ApiResponse<ReanalyzeResponse>> reanalyze(
            @PathVariable Long analysisId,
            @RequestParam String type) {
        Long userId = getCurrentUserId();
        ReanalyzeResponse response = userService.reanalyze(userId, analysisId, type);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            throw new AuthException("인증 정보를 찾을 수 없습니다.");
        }
        return (Long) auth.getPrincipal();
    }
}
