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
import ssafy.SSAju.career.domain.AnalysisExecution;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.mapper.AnalysisSnapshotMapper;
import ssafy.SSAju.exception.UnauthorizedException;
import ssafy.SSAju.handler.SajuGlobalExceptionHandler;
import ssafy.SSAju.service.AnalysisService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CareerTimingController HTTP 레이어 테스트")
class CareerTimingControllerTest {

    private static final String VALID_REQUEST_BODY = """
            {"birthDate": "1990-10-10", "birthTime": "14:30"}
            """;

    @Mock
    private AnalysisService analysisService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CareerTimingController(analysisService, new AnalysisSnapshotMapper()))
                .setControllerAdvice(new SajuGlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 사용자 + 유효한 요청 → 200 OK, 숫자 분석 ID 반환")
    void shouldReturn200_WhenAuthenticatedAndValidRequest() throws Exception {
        // Given
        authenticate(1L);
        given(analysisService.analyze(eq(AnalysisType.SAJU), any(), eq(1L)))
                .willReturn(execution(42L));

        // When / Then
        mockMvc.perform(post("/api/career/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sajuResultId").value(42))
                .andExpect(jsonPath("$.data.sajuResultId").isNumber())
                .andExpect(jsonPath("$.data.favoredPeriod").value("H1"))
                .andExpect(jsonPath("$.data.confidenceScore").value(75))
                .andExpect(jsonPath("$.data.reasoning").isNotEmpty());
    }

    @Test
    @DisplayName("같은 입력을 반복 요청해도 매번 다른 분석 ID를 돌려준다")
    void shouldReturnNewAnalysisIdForRepeatedRequest() throws Exception {
        // Given
        authenticate(1L);
        given(analysisService.analyze(eq(AnalysisType.SAJU), any(), eq(1L)))
                .willReturn(execution(42L), execution(43L));

        // When / Then
        mockMvc.perform(post("/api/career/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_BODY))
                .andExpect(jsonPath("$.data.sajuResultId").value(42));
        mockMvc.perform(post("/api/career/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_BODY))
                .andExpect(jsonPath("$.data.sajuResultId").value(43));
    }

    @Test
    @DisplayName("미인증 요청 → 기존 정책대로 403 거부")
    void shouldRejectUnauthenticatedRequest() throws Exception {
        // Given
        given(analysisService.analyze(eq(AnalysisType.SAJU), any(), eq(null)))
                .willThrow(new UnauthorizedException("인증 정보가 없습니다. 로그인 후 시도해주세요."));

        // When / Then
        mockMvc.perform(post("/api/career/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("birthDate 누락 → 400 Bad Request")
    void shouldReturn400_WhenBirthDateMissing() throws Exception {
        mockMvc.perform(post("/api/career/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthTime": "14:30"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("birthTime 누락 → 400 Bad Request")
    void shouldReturn400_WhenBirthTimeMissing() throws Exception {
        mockMvc.perform(post("/api/career/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1990-10-10"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("빈 바디 → 400 Bad Request")
    void shouldReturn400_WhenEmptyBody() throws Exception {
        mockMvc.perform(post("/api/career/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private AnalysisExecution execution(Long analysisId) {
        return new AnalysisExecution(analysisId,
                AnalysisPayload.SajuResultData.of("H1", 75, "상반기가 취업에 유리합니다."));
    }
}
