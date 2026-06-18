package ssafy.SSAju.admin.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ssafy.SSAju.admin.service.AdminDashboardService;
import ssafy.SSAju.admin.dto.DashboardDTO;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminDashboard 통합 테스트 (T032).
 * H2 인메모리 DB 사용. 실제 서비스-리포지토리 레이어를 통해 데이터 정합성 검증.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("AdminDashboard 통합 테스트 (T032)")
class AdminDashboardIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AdminDashboardService adminDashboardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("GET /admin/api/dashboard → ADMIN 권한으로 정상 응답")
    void getDashboardJson_withAdminRole_returns200() throws Exception {
        mockMvc.perform(get("/admin/api/dashboard")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("admin@test.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalAnalysis").isNumber())
                .andExpect(jsonPath("$.dailyLimitExhaustedCount").isNumber())
                .andExpect(jsonPath("$.feedbackSummary").exists());
    }

    @Test
    @DisplayName("GET /admin/api/dashboard → 미인증 요청 시 401 또는 리다이렉트")
    void getDashboardJson_unauthenticated_returns401OrRedirect() throws Exception {
        mockMvc.perform(get("/admin/api/dashboard")
                        .accept(MediaType.APPLICATION_JSON)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getDashboardData() → 빈 DB에서 0 값 반환, NPE 없음")
    void getDashboardData_emptyDb_returnsZerosWithoutException() {
        DashboardDTO result = adminDashboardService.getDashboardData();

        assertThat(result).isNotNull();
        assertThat(result.totalAnalysis()).isEqualTo(0L);
        assertThat(result.dailyLimitExhaustedCount()).isEqualTo(0L);
        assertThat(result.feedbackSummary()).isNotNull();
        assertThat(result.feedbackSummary().satisfiedCount()).isEqualTo(0L);
        assertThat(result.feedbackSummary().unsatisfiedCount()).isEqualTo(0L);
        assertThat(result.feedbackSummary().totalCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("GET /admin/api/dashboard → 5초 이내 응답 (SC-001)")
    void getDashboardJson_respondsWithinFiveSeconds() {
        assertTimeout(Duration.ofSeconds(5), () ->
                mockMvc.perform(get("/admin/api/dashboard")
                                .accept(MediaType.APPLICATION_JSON)
                                .with(user("admin@test.com").roles("ADMIN")))
                        .andExpect(status().isOk())
        );
    }
}
