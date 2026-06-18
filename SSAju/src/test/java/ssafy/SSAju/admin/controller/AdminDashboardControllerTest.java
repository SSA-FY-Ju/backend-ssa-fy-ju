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
import org.springframework.web.servlet.view.InternalResourceView;
import ssafy.SSAju.admin.config.AdminConfig;
import ssafy.SSAju.admin.dto.DashboardDTO;
import ssafy.SSAju.admin.service.AdminDashboardService;

import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminDashboardController HTTP 레이어 테스트")
class AdminDashboardControllerTest {

    @Mock
    private AdminDashboardService adminDashboardService;

    @Mock
    private AdminConfig adminConfig;

    private MockMvc mockMvc;

    private DashboardDTO sampleDashboard() {
        return new DashboardDTO(
                10L,
                Map.of("SAJU", 5L, "CAREER_CONSULTATION", 3L, "COMPANY_COMPATIBILITY", 2L),
                2L,
                new DashboardDTO.FeedbackSummary(8L, 3L, 11L)
        );
    }

    @BeforeEach
    void setUp() {
        AdminConfig.Api api = new AdminConfig.Api();
        api.setDailyLimit(3);
        lenient().when(adminConfig.getApi()).thenReturn(api);

        AdminDashboardController controller = new AdminDashboardController(adminDashboardService, adminConfig);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setViewResolvers((viewName, locale) -> new InternalResourceView(viewName))
                .build();
    }

    @Test
    @DisplayName("GET /admin/dashboard → admin/dashboard 뷰 반환 + 모델 속성 포함")
    void getDashboard_returnsHtmlViewWithModel() throws Exception {
        given(adminDashboardService.getDashboardData()).willReturn(sampleDashboard());

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists("dashboard"))
                .andExpect(model().attribute("currentMenu", "dashboard"))
                .andExpect(model().attribute("pageTitle", "관리자 대시보드"));
    }

    @Test
    @DisplayName("GET /admin/api/dashboard → JSON 응답 반환")
    void getDashboardJson_returnsJsonWithCorrectFields() throws Exception {
        given(adminDashboardService.getDashboardData()).willReturn(sampleDashboard());

        mockMvc.perform(get("/admin/api/dashboard").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalAnalysis").value(10))
                .andExpect(jsonPath("$.dailyLimitExhaustedCount").value(2))
                .andExpect(jsonPath("$.feedbackSummary.satisfiedCount").value(8))
                .andExpect(jsonPath("$.feedbackSummary.unsatisfiedCount").value(3))
                .andExpect(jsonPath("$.feedbackSummary.totalCount").value(11))
                .andExpect(jsonPath("$.analysisTypeBreakdown.SAJU").value(5));
    }

    @Test
    @DisplayName("GET /admin/api/dashboard → 5초 이내 응답")
    void getDashboardJson_respondsWithinFiveSeconds() throws Exception {
        given(adminDashboardService.getDashboardData()).willReturn(sampleDashboard());

        long start = System.currentTimeMillis();
        mockMvc.perform(get("/admin/api/dashboard").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        long elapsed = System.currentTimeMillis() - start;

        org.assertj.core.api.Assertions.assertThat(elapsed).isLessThan(5_000L);
    }

    @Test
    @DisplayName("GET /admin/dashboard → 데이터 없을 때 totalAnalysis = 0 처리")
    void getDashboard_emptyData_returnsZeroValues() throws Exception {
        DashboardDTO empty = new DashboardDTO(0L, Map.of(), 0L, new DashboardDTO.FeedbackSummary(0L, 0L, 0L));
        given(adminDashboardService.getDashboardData()).willReturn(empty);

        mockMvc.perform(get("/admin/api/dashboard").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAnalysis").value(0))
                .andExpect(jsonPath("$.dailyLimitExhaustedCount").value(0))
                .andExpect(jsonPath("$.feedbackSummary.totalCount").value(0));
    }
}
