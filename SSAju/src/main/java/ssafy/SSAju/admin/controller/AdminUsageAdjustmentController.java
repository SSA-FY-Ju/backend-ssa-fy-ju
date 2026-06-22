package ssafy.SSAju.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.SSAju.admin.dto.UsageAdjustmentRequestDTO;
import ssafy.SSAju.admin.dto.UsageAdjustmentResponseDTO;
import ssafy.SSAju.admin.service.AdminUsageAdjustmentService;

@Slf4j
@RestController
@RequestMapping("/admin/daily-usages")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUsageAdjustmentController {

    private final AdminUsageAdjustmentService usageAdjustmentService;

    @PostMapping("/users/{userId}/adjust")
    public ResponseEntity<UsageAdjustmentResponseDTO> adjustDailyUsage(
            @PathVariable Long userId,
            @RequestBody UsageAdjustmentRequestDTO request) {
        UsageAdjustmentResponseDTO result = usageAdjustmentService.adjustDailyUsage(userId, request);
        return ResponseEntity.ok(result);
    }
}
