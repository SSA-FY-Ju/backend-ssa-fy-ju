package ssafy.SSAju.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.SSAju.exception.AuthException;
import ssafy.SSAju.dto.request.SatisfactionFeedbackRequest;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.SatisfactionFeedbackResponse;
import ssafy.SSAju.service.FeedbackService;

@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
@Tag(name = "피드백", description = "사용자 만족도 피드백 수집 API")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/satisfaction")
    @Operation(summary = "만족도 피드백 저장", description = "커리어 상담 결과에 대한 사용자 만족도 및 피드백 저장")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "피드백 저장 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 (sajuResultId/feedbackType/satisfactionStatus 누락)", content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 SajuResult", content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<ApiResponse<SatisfactionFeedbackResponse>> submitFeedback(
            @Valid @RequestBody SatisfactionFeedbackRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        if (userId == null) {
            throw new AuthException("인증 정보를 찾을 수 없습니다.");
        }
        log.info("만족도 피드백 요청 수신: userId={}", userId);
        SatisfactionFeedbackResponse response = feedbackService.saveFeedback(request, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
