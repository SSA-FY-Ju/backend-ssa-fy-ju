package ssafy.SSAju.admin.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ssafy.SSAju.admin.dto.FeedbackDetailDTO;
import ssafy.SSAju.admin.dto.FeedbackListDTO;
import ssafy.SSAju.admin.dto.FeedbackStatDTO;
import ssafy.SSAju.admin.service.AdminFeedbackService;
import ssafy.SSAju.career.enums.FeedbackType;
import ssafy.SSAju.career.enums.SatisfactionStatus;
import ssafy.SSAju.exception.FeedbackNotFoundException;
import ssafy.SSAju.handler.SajuGlobalExceptionHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminFeedbackController HTTP 레이어 테스트")
class AdminFeedbackControllerTest {

    @Mock
    private AdminFeedbackService feedbackService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminFeedbackController controller = new AdminFeedbackController(feedbackService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new SajuGlobalExceptionHandler())
                .build();
    }

    private FeedbackListDTO stubFeedbackItem(long id) {
        return new FeedbackListDTO(id, 10L, "좋아요", SatisfactionStatus.SATISFIED,
                FeedbackType.CONSULTATION, Instant.now());
    }

    private FeedbackStatDTO stubStats() {
        return new FeedbackStatDTO(
                Map.of("CONSULTATION", 5L),
                Map.of("CONSULTATION", 2L),
                7L
        );
    }

    @Test
    @DisplayName("GET /admin/api/feedback → 200 + JSON 목록 반환")
    void getFeedbackApi_returns200WithList() throws Exception {
        Page<FeedbackListDTO> page = new PageImpl<>(List.of(stubFeedbackItem(1L), stubFeedbackItem(2L)));
        given(feedbackService.getFeedbackList(isNull(), anyInt(), anyInt())).willReturn(page);

        mockMvc.perform(get("/admin/api/feedback")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    @DisplayName("GET /admin/api/feedback?type=CONSULTATION → type 파라미터 서비스에 전달")
    void getFeedbackApi_withTypeFilter_passesToService() throws Exception {
        Page<FeedbackListDTO> page = new PageImpl<>(List.of(stubFeedbackItem(1L)));
        given(feedbackService.getFeedbackList(eq("CONSULTATION"), anyInt(), anyInt())).willReturn(page);

        mockMvc.perform(get("/admin/api/feedback")
                        .param("type", "CONSULTATION")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @DisplayName("GET /admin/api/feedback → 빈 결과 → 200 + 빈 배열")
    void getFeedbackApi_emptyResult_returns200WithEmptyPage() throws Exception {
        Page<FeedbackListDTO> emptyPage = new PageImpl<>(List.of());
        given(feedbackService.getFeedbackList(any(), anyInt(), anyInt())).willReturn(emptyPage);

        mockMvc.perform(get("/admin/api/feedback")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /admin/api/feedback/stats → 200 + 통계 JSON 반환")
    void getFeedbackStats_returns200WithStats() throws Exception {
        given(feedbackService.getFeedbackStats()).willReturn(stubStats());

        mockMvc.perform(get("/admin/api/feedback/stats")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFeedbackCount").value(7))
                .andExpect(jsonPath("$.satisfiedCountByFeedbackType.CONSULTATION").value(5));
    }

    @Test
    @DisplayName("GET /admin/api/feedback/{id} → 200 + 상세 정보 반환")
    void getFeedbackDetail_existingId_returns200() throws Exception {
        FeedbackDetailDTO detail = new FeedbackDetailDTO(
                1L, 10L, "CONSULTATION", "SATISFIED", "좋았습니다",
                Instant.now(), "CONSULTATION", 100L, Instant.now());
        given(feedbackService.getFeedbackWithAnalysis(1L)).willReturn(detail);

        mockMvc.perform(get("/admin/api/feedback/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackId").value(1))
                .andExpect(jsonPath("$.analysisType").value("CONSULTATION"))
                .andExpect(jsonPath("$.analysisId").value(100));
    }

    @Test
    @DisplayName("GET /admin/api/feedback/{id} → 존재하지 않는 id → 404")
    void getFeedbackDetail_notFound_returns404() throws Exception {
        given(feedbackService.getFeedbackWithAnalysis(999L))
                .willThrow(new FeedbackNotFoundException("피드백을 찾을 수 없습니다: id=999"));

        mockMvc.perform(get("/admin/api/feedback/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /admin/api/feedback?page=2 → page 파라미터 서비스에 전달")
    void getFeedbackApi_withPage_passesToService() throws Exception {
        Page<FeedbackListDTO> page = new PageImpl<>(List.of());
        given(feedbackService.getFeedbackList(isNull(), eq(2), anyInt())).willReturn(page);

        mockMvc.perform(get("/admin/api/feedback")
                        .param("page", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /admin/api/feedback?type=INVALID → 400")
    void getFeedbackApi_invalidType_returns400() throws Exception {
        given(feedbackService.getFeedbackList(eq("INVALID"), anyInt(), anyInt()))
                .willThrow(new IllegalArgumentException("유효하지 않은 피드백 유형입니다: INVALID"));

        mockMvc.perform(get("/admin/api/feedback")
                        .param("type", "INVALID")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
