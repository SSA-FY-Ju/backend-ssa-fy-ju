package ssafy.SSAju.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.exception.OpenAIApiException;
import ssafy.SSAju.handler.SajuGlobalExceptionHandler;
import ssafy.SSAju.service.ConsultationService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultationController HTTP 레이어 테스트")
class ConsultationControllerTest {

    @Mock
    private ConsultationService consultationService;

    private MockMvc mockMvc;

    private static final ConsultationResponse MOCK_RESPONSE = new ConsultationResponse(
            List.of(Map.of("name", "금융/핀테크", "reason", "오행 金 강세")),
            List.of("일관성 있는 자기소개 준비", "데이터 기반 성과 강조"),
            List.of("분석력과 논리성", "책임감"),
            "gpt-4o-mini"
    );

    private static final String VALID_REQUEST_BODY = """
            {
              "birthDate": "1990-10-10",
              "birthTime": "14:30"
            }
            """;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConsultationController(consultationService))
                .setControllerAdvice(new SajuGlobalExceptionHandler())
                .build();
    }

    // ─────────────────────────────────────────
    // 정상 플로우
    // ─────────────────────────────────────────

    @Test
    @DisplayName("유효한 요청 → 200 OK + 컨설팅 응답")
    void shouldReturn200_WhenValidRequest() throws Exception {
        given(consultationService.getCareerConsultation(any())).willReturn(MOCK_RESPONSE);

        mockMvc.perform(post("/api/career/consultation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.industries").isArray())
                .andExpect(jsonPath("$.data.interviewTips").isArray())
                .andExpect(jsonPath("$.data.strengths").isArray())
                .andExpect(jsonPath("$.data.openaiModelVersion").value("gpt-4o-mini"));
    }

    // ─────────────────────────────────────────
    // 입력값 검증 실패
    // ─────────────────────────────────────────

    @Test
    @DisplayName("birthTime 누락 → 400 Bad Request")
    void shouldReturn400_WhenBirthTimeMissing() throws Exception {
        mockMvc.perform(post("/api/career/consultation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "birthDate": "1990-10-10"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("빈 요청 바디 → 400 Bad Request")
    void shouldReturn400_WhenEmptyBody() throws Exception {
        mockMvc.perform(post("/api/career/consultation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────
    // 서비스 예외 전파
    // ─────────────────────────────────────────

    @Test
    @DisplayName("OpenAI 타임아웃 → 504 Gateway Timeout")
    void shouldReturn504_WhenOpenAITimeout() throws Exception {
        given(consultationService.getCareerConsultation(any()))
                .willThrow(new OpenAIApiException("OpenAI API 요청 시간 초과"));

        mockMvc.perform(post("/api/career/consultation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_BODY))
                .andExpect(status().isGatewayTimeout());
    }
}
