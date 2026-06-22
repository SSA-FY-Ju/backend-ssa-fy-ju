package ssafy.SSAju.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ssafy.SSAju.admin.dto.AdjustmentAction;
import ssafy.SSAju.admin.dto.UsageAdjustmentRequestDTO;
import ssafy.SSAju.admin.dto.UsageAdjustmentResponseDTO;
import ssafy.SSAju.admin.service.AdminUsageAdjustmentService;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.handler.SajuGlobalExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUsageAdjustmentController HTTP 레이어 테스트")
class AdminUsageAdjustmentControllerTest {

    @Mock
    private AdminUsageAdjustmentService usageAdjustmentService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AdminUsageAdjustmentController controller = new AdminUsageAdjustmentController(usageAdjustmentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new SajuGlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /admin/daily-usages/users/{userId}/adjust - RESET → 200 + 결과 반환")
    void adjustDailyUsage_reset_returns200() throws Exception {
        UsageAdjustmentResponseDTO response = new UsageAdjustmentResponseDTO(
                1L, "2026-06-22", 3, 0, "RESET");
        given(usageAdjustmentService.adjustDailyUsage(eq(1L), any())).willReturn(response);

        mockMvc.perform(post("/admin/daily-usages/users/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UsageAdjustmentRequestDTO(AdjustmentAction.RESET, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("RESET"))
                .andExpect(jsonPath("$.usageCountBefore").value(3))
                .andExpect(jsonPath("$.usageCountAfter").value(0));
    }

    @Test
    @DisplayName("POST /admin/daily-usages/users/{userId}/adjust - DECREMENT 1 → 200 + 결과 반환")
    void adjustDailyUsage_decrement_returns200() throws Exception {
        UsageAdjustmentResponseDTO response = new UsageAdjustmentResponseDTO(
                1L, "2026-06-22", 3, 2, "DECREMENT");
        given(usageAdjustmentService.adjustDailyUsage(eq(1L), any())).willReturn(response);

        mockMvc.perform(post("/admin/daily-usages/users/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UsageAdjustmentRequestDTO(AdjustmentAction.DECREMENT, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usageCountBefore").value(3))
                .andExpect(jsonPath("$.usageCountAfter").value(2));
    }

    @Test
    @DisplayName("POST /admin/daily-usages/users/{userId}/adjust - 잘못된 action → 400 (Jackson 역직렬화 실패)")
    void adjustDailyUsage_invalidAction_returns400() throws Exception {
        mockMvc.perform(post("/admin/daily-usages/users/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\": \"DELETE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /admin/daily-usages/users/{userId}/adjust - 존재하지 않는 userId → 404")
    void adjustDailyUsage_userNotFound_returns404() throws Exception {
        given(usageAdjustmentService.adjustDailyUsage(eq(999L), any()))
                .willThrow(new UserNotFoundException("userId=999"));

        mockMvc.perform(post("/admin/daily-usages/users/999/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UsageAdjustmentRequestDTO(AdjustmentAction.RESET, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /admin/daily-usages/users/{userId}/adjust - DECREMENT amount 0 → 400")
    void adjustDailyUsage_decrementAmountZero_returns400() throws Exception {
        given(usageAdjustmentService.adjustDailyUsage(eq(1L), any()))
                .willThrow(new IllegalArgumentException("DECREMENT 시 amount는 1 이상이어야 합니다."));

        mockMvc.perform(post("/admin/daily-usages/users/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UsageAdjustmentRequestDTO(AdjustmentAction.DECREMENT, 0))))
                .andExpect(status().isBadRequest());
    }
}
