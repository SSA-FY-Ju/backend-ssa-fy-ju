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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.service.CompanyMatchingService;
import ssafy.SSAju.service.UserService;

import java.time.LocalTime;

@Slf4j
@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
@Tag(name = "기업 궁합", description = "사용자 사주와 기업 설립일 기반 궁합 분석 API")
public class CompatibilityController {

    private final CompanyMatchingService companyMatchingService;
    private final UserService userService;

    private static final LocalTime DEFAULT_BIRTH_TIME = LocalTime.of(12, 0);

    @PostMapping("/compatibility")
    @Operation(summary = "기업 궁합 분석", description = "사용자 사주와 기업 설립일의 십신+지장간 분석을 통한 호환성 점수 및 직군 적합도 반환")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "기업 궁합 분석 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 (userBirthDate/targetRole/companyName 누락)", content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "기업 정보 미발견 (공공데이터 API)", content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "FastAPI 타임아웃", content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<ApiResponse<CompatibilityResponse>> analyzeCompatibility(
            @Valid @RequestBody CompatibilityRequest request
    ) {
        log.info("기업 궁합 분석 요청 수신: company={}", request.companyName());
        CompatibilityResponse response = companyMatchingService.analyzeCompatibility(request);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            try {
                LocalTime birthTime = request.userBirthTime() != null
                        ? request.userBirthTime() : DEFAULT_BIRTH_TIME;
                userService.associateCompatibilityWithUser(userId,
                        request.userBirthDate(), birthTime,
                        request.companyName(), request.targetRole().category());
            } catch (Exception e) {
                log.warn("사용자 연결 실패 (무시됨): userId={}", userId, e);
            }
        }

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
