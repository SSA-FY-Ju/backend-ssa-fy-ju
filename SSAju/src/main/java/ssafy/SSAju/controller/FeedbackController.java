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
    ) {
        log.info("만족도 피드백 요청 수신");
        SatisfactionFeedbackResponse response = feedbackService.saveFeedback(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
