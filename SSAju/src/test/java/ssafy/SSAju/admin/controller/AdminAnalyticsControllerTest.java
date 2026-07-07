package ssafy.SSAju.admin.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ssafy.SSAju.admin.dto.AnalyticsDetailDTO;
import ssafy.SSAju.admin.dto.AnalyticsListDTO;
import ssafy.SSAju.admin.service.AdminAnalyticsService;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.handler.SajuGlobalExceptionHandler;

import java.time.Instant;
import java.util.List;

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
@DisplayName("AdminAnalyticsController HTTP 레이어 테스트")
class AdminAnalyticsControllerTest {

    @Mock
    private AdminAnalyticsService adminAnalyticsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminAnalyticsController controller = new AdminAnalyticsController(adminAnalyticsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new SajuGlobalExceptionHandler())
                .build();
    }

    private AnalyticsListDTO stubListItem(long id, String type) {
        return new AnalyticsListDTO(id, 1L, type, Instant.now());
    }

    @Test
    @DisplayName("GET /admin/analytics/api → 200 + JSON 목록 반환")
    void getAnalyticsApi_returns200WithList() throws Exception {
        given(adminAnalyticsService.getAnalyticsHistory(isNull(), any(), any(), anyInt(), anyInt()))
                .willReturn(List.of(
                        stubListItem(3L, "SAJU"),
                        stubListItem(2L, "CAREER_CONSULTATION")
                ));

        mockMvc.perform(get("/admin/analytics/api")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].analysisType").value("SAJU"))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    @DisplayName("GET /admin/analytics/api?type=SAJU → type 파라미터 서비스에 전달")
    void getAnalyticsApi_withTypeFilter_passesToService() throws Exception {
        given(adminAnalyticsService.getAnalyticsHistory(eq(AnalysisType.SAJU), any(), any(), anyInt(), anyInt()))
                .willReturn(List.of(stubListItem(1L, "SAJU")));

        mockMvc.perform(get("/admin/analytics/api")
                        .param("type", "SAJU")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].analysisType").value("SAJU"));
    }

    @Test
    @DisplayName("GET /admin/analytics/api?page=2 → page 파라미터 서비스에 전달")
    void getAnalyticsApi_withPage_passesToService() throws Exception {
        given(adminAnalyticsService.getAnalyticsHistory(isNull(), any(), any(), eq(2), anyInt()))
                .willReturn(List.of());

        mockMvc.perform(get("/admin/analytics/api")
                        .param("page", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /admin/analytics/{id}?type=SAJU → 200 + 상세 정보 반환")
    void getAnalyticsDetail_existingId_returns200() throws Exception {
        AnalyticsDetailDTO detail = new AnalyticsDetailDTO(
                1L, 10L, "SAJU", "{\"key\":\"값\"}", Instant.now());
        given(adminAnalyticsService.getAnalyticsDetail(1L, AnalysisType.SAJU)).willReturn(detail);

        mockMvc.perform(get("/admin/analytics/1")
                        .param("type", "SAJU")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.analysisType").value("SAJU"))
                .andExpect(jsonPath("$.jsonData").value("{\"key\":\"값\"}"));
    }

    @Test
    @DisplayName("GET /admin/analytics/{id}?type=SAJU → 존재하지 않는 id → 400")
    void getAnalyticsDetail_notFound_returns400() throws Exception {
        given(adminAnalyticsService.getAnalyticsDetail(999L, AnalysisType.SAJU))
                .willThrow(new IllegalArgumentException("분석 기록을 찾을 수 없습니다: id=999"));

        mockMvc.perform(get("/admin/analytics/999")
                        .param("type", "SAJU")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /admin/analytics/api → 빈 결과 → 200 + 빈 배열")
    void getAnalyticsApi_emptyResult_returns200WithEmptyArray() throws Exception {
        given(adminAnalyticsService.getAnalyticsHistory(any(), any(), any(), anyInt(), anyInt()))
                .willReturn(List.of());

        mockMvc.perform(get("/admin/analytics/api")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
