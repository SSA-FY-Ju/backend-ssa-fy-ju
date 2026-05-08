package ssafy.SSAju.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.dto.response.ApiResponse;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.service.CompanyMatchingService;

@Slf4j
@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompatibilityController {

    private final CompanyMatchingService companyMatchingService;

    @PostMapping("/compatibility")
    public ResponseEntity<ApiResponse<CompatibilityResponse>> analyzeCompatibility(
            @Valid @RequestBody CompatibilityRequest request
    ) {
        log.info("기업 궁합 분석 요청 수신: company={}", request.companyName());
        CompatibilityResponse response = companyMatchingService.analyzeCompatibility(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
