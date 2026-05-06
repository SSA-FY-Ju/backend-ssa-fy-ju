package ssafy.SSAju.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.SSAju.dto.request.SatisfactionFeedbackRequest;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.SatisfactionFeedbackResponse;
import ssafy.SSAju.service.FeedbackService;

@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/satisfaction")
    public ResponseEntity<ApiResponse<SatisfactionFeedbackResponse>> submitFeedback(
            @Valid @RequestBody SatisfactionFeedbackRequest request
            // TODO: Phase 2 로그인 추가 시 @AuthenticationPrincipal UserPrincipal user 추가
            // - 현재는 인증 없음 (SecurityConfig.permitAll)
            // - Phase 2에서 인증 컨텍스트 추가 후 현재 사용자 정보 받기
    ) {
        log.info("만족도 피드백 요청 수신");
        // TODO: Phase 2에서 user 정보를 FeedbackService에 전달
        SatisfactionFeedbackResponse response = feedbackService.saveFeedback(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
