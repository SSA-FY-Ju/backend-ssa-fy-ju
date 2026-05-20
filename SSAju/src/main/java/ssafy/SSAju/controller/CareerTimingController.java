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
import ssafy.SSAju.dto.request.CareerTimingRequest;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.CareerTimingResponse;
import ssafy.SSAju.service.CareerFortuneService;

@Slf4j
@RestController
@RequestMapping("/api/career")
@RequiredArgsConstructor
@Tag(name = "관운 분석", description = "생년월일시 기반 직업 관련 운세 분석 API")
public class CareerTimingController {

    private final CareerFortuneService careerFortuneService;

    @PostMapping("/timing")
    @Operation(summary = "관운 분석", description = "생년월일시로부터 상반기(H1)/하반기(H2) 관운 판정, 신뢰도 점수 및 근거 반환")
    // TODO: ApiResponse 클래스 이름 변경 후 import 정리
    // (현재: 내부 dto.response.ApiResponse와 Swagger @ApiResponse 이름 충돌)
    // 예: ApiResponse → ApiResponseDto로 변경 후 @ApiResponse로 간결화
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관운 분석 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 (birthDate/birthTime 누락)", content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "FastAPI 타임아웃", content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<ApiResponse<CareerTimingResponse>> getCareerTiming(
            @Valid @RequestBody CareerTimingRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        log.info("관운 분석 요청 수신");
        CareerTimingResponse response = careerFortuneService.analyzeCareerTiming(
                request.birthDate(), request.birthTime(), userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
