package ssafy.SSAju.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
public class CareerTimingController {

    private final CareerFortuneService careerFortuneService;

    @PostMapping("/timing")
    public ResponseEntity<ApiResponse<CareerTimingResponse>> getCareerTiming(
            @Valid @RequestBody CareerTimingRequest request
    ) {
        log.info("관운 분석 요청 수신");
        CareerTimingResponse response = careerFortuneService.analyzeCareerTiming(
                request.birthDate(), request.birthTime());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
