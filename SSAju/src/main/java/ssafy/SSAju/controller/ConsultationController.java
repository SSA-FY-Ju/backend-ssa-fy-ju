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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.SSAju.dto.request.ConsultationRequest;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.service.ConsultationService;

@Slf4j
@RestController
@RequestMapping("/api/career")
@RequiredArgsConstructor
@Tag(name = "커리어 상담", description = "사주 + AI 기반 커리어 컨설팅 API (23개 필드)")
public class ConsultationController {

    private final ConsultationService consultationService;

    @PostMapping("/consultation")
    @Operation(summary = "AI 커리어 상담", description = "사주 데이터(십신+지장간)와 OpenAI를 활용하여 23개 필드의 커리어 조언 반환")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "커리어 상담 성공 (23개 필드)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 (birthDate/birthTime 누락)", content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "504", description = "OpenAI API 타임아웃", content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<ApiResponse<ConsultationResponse>> getCareerConsultation(
            @Valid @RequestBody ConsultationRequest request
    ) {
        log.info("커리어 컨설팅 요청 수신");
        ConsultationResponse response = consultationService.getCareerConsultation(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
