package ssafy.SSAju.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ssafy.SSAju.career.enums.ForecastStatus;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.exception.FastAPITimeoutException;
import ssafy.SSAju.handler.SajuGlobalExceptionHandler;
import ssafy.SSAju.service.CompanyMatchingService;
import ssafy.SSAju.service.UserService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompatibilityController HTTP 레이어 테스트")
class CompatibilityControllerTest {

    @Mock
    private CompanyMatchingService companyMatchingService;

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    private static final CompatibilityResponse MOCK_RESPONSE = new CompatibilityResponse(
            new CompatibilityResponse.RequestContext(
                    "현대오토에버",
                    new CompatibilityResponse.TargetRoleInfo(JobCategoryEnum.TECH_BACKEND, "백엔드 개발자")
            ),
            78, "상생의 궁합입니다.",
            new CompatibilityResponse.TargetRoleAnalysis(85, "시너지", "경고"),
            new CompatibilityResponse.FiveElements(
                    Map.of("金", 2, "水", 1),
                    Map.of("土", 2, "金", 1),
                    "균형 잡힌 오행 분포"
            ),
            new CompatibilityResponse.AnalysisBreakdown(83, 73, 78),
            new CompatibilityResponse.ActionableStrategy(
                    List.of("체계적 설계", "논리적 사고"),
                    "약점 방어 전략입니다.",
                    new CompatibilityResponse.ActionableStrategy.BestTiming(
                            List.of("2026-05-12"), "오전 09:00 ~ 11:00")
            ),
            List.of(new CompatibilityResponse.InterviewQuestion("예상 질문", "의도")),
            List.of(new CompatibilityResponse.RoleCompatibility("백엔드 전문가", 88, "이유", "강력 추천")),
            List.of(new CompatibilityResponse.MonthlyForecast(5, 75, ForecastStatus.LUCKY, "조언")),
            List.of("주의사항 1")
    );

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CompatibilityController(companyMatchingService, userService))
                .setControllerAdvice(new SajuGlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 요청 → 200 OK + 궁합 점수 반환")
    void shouldReturn200_WhenValidRequest() throws Exception {
        given(companyMatchingService.analyzeCompatibility(any())).willReturn(MOCK_RESPONSE);

        mockMvc.perform(post("/api/company/compatibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userBirthDate": "1998-05-07",
                                  "userBirthTime": "14:30",
                                  "targetRole": {
                                    "category": "TECH_BACKEND",
                                    "detailName": "백엔드 개발자"
                                  },
                                  "companyName": "현대오토에버",
                                  "companyFoundingDate": "2000-04-10",
                                  "companyFoundingTime": "12:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.compatibilityScore").value(78))
                .andExpect(jsonPath("$.data.requestContext.companyName").value("현대오토에버"));
    }

    @Test
    @DisplayName("userBirthDate 누락 → 400 Bad Request")
    void shouldReturn400_WhenUserBirthDateMissing() throws Exception {
        mockMvc.perform(post("/api/company/compatibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userBirthTime": "14:30",
                                  "targetRole": { "category": "TECH_BACKEND" },
                                  "companyName": "현대오토에버",
                                  "companyFoundingDate": "2000-04-10"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("targetRole.category 누락 → 400 Bad Request")
    void shouldReturn400_WhenTargetRoleCategoryMissing() throws Exception {
        mockMvc.perform(post("/api/company/compatibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userBirthDate": "1998-05-07",
                                  "targetRole": { "detailName": "백엔드 개발자" },
                                  "companyName": "현대오토에버",
                                  "companyFoundingDate": "2000-04-10"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("companyName 누락 → 400 Bad Request")
    void shouldReturn400_WhenCompanyNameMissing() throws Exception {
        mockMvc.perform(post("/api/company/compatibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userBirthDate": "1998-05-07",
                                  "targetRole": { "category": "TECH_BACKEND" },
                                  "companyFoundingDate": "2000-04-10"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("인증된 사용자 요청 → associateCompatibilityWithUser 호출됨")
    void shouldAssociateUser_WhenAuthenticated() throws Exception {
        Long userId = 1L;
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));

        given(companyMatchingService.analyzeCompatibility(any())).willReturn(MOCK_RESPONSE);

        mockMvc.perform(post("/api/company/compatibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userBirthDate": "1998-05-07",
                                  "userBirthTime": "14:30",
                                  "targetRole": {
                                    "category": "TECH_BACKEND",
                                    "detailName": "백엔드 개발자"
                                  },
                                  "companyName": "현대오토에버",
                                  "companyFoundingDate": "2000-04-10"
                                }
                                """))
                .andExpect(status().isOk());

        verify(userService).associateCompatibilityWithUser(
                eq(userId), any(LocalDate.class), any(LocalTime.class),
                any(String.class), any(JobCategoryEnum.class));
    }

    @Test
    @DisplayName("비인증 요청 → associateCompatibilityWithUser 호출 안 됨")
    void shouldNotAssociateUser_WhenNotAuthenticated() throws Exception {
        given(companyMatchingService.analyzeCompatibility(any())).willReturn(MOCK_RESPONSE);

        mockMvc.perform(post("/api/company/compatibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userBirthDate": "1998-05-07",
                                  "userBirthTime": "14:30",
                                  "targetRole": { "category": "TECH_BACKEND" },
                                  "companyName": "현대오토에버",
                                  "companyFoundingDate": "2000-04-10"
                                }
                                """))
                .andExpect(status().isOk());

        verify(userService, never()).associateCompatibilityWithUser(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("FastAPI 타임아웃 → 503 Service Unavailable")
    void shouldReturn503_WhenFastAPITimeout() throws Exception {
        given(companyMatchingService.analyzeCompatibility(any()))
                .willThrow(new FastAPITimeoutException("FastAPI 응답 타임아웃"));

        mockMvc.perform(post("/api/company/compatibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userBirthDate": "1998-05-07",
                                  "userBirthTime": "14:30",
                                  "targetRole": { "category": "TECH_BACKEND" },
                                  "companyName": "현대오토에버",
                                  "companyFoundingDate": "2000-04-10"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }
}
