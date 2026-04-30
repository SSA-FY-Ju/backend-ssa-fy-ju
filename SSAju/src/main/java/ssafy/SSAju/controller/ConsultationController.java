package ssafy.SSAju.controller;

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
public class ConsultationController {

    private final ConsultationService consultationService;

    @PostMapping("/consultation")
    public ResponseEntity<ApiResponse<ConsultationResponse>> getCareerConsultation(
            @Valid @RequestBody ConsultationRequest request
    ) {
        log.info("커리어 컨설팅 요청 수신");
        ConsultationResponse response = consultationService.getCareerConsultation(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
