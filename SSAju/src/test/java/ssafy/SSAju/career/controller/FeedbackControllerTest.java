package ssafy.SSAju.career.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ssafy.SSAju.career.dto.response.SatisfactionFeedbackResponse;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.service.FeedbackService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("FeedbackController HTTP 레이어 테스트")
class FeedbackControllerTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private FeedbackService feedbackService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken(1L, null, List.of());

    @Test
    @DisplayName("유효한 피드백 요청 → 200 OK + feedbackId 반환")
    void shouldReturn200_WhenValidRequest() throws Exception {
        given(feedbackService.saveFeedback(any(), anyLong()))
                .willReturn(new SatisfactionFeedbackResponse(1L, Instant.now(), "좋았습니다"));

        mockMvc.perform(post("/api/feedback/satisfaction")
                        .with(authentication(AUTH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "analysisId": 1,
                                  "feedbackType": "CAREER_CONSULTATION",
                                  "satisfactionStatus": "SATISFIED",
                                  "feedbackContent": "좋았습니다"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.feedbackId").value(1))
                .andExpect(jsonPath("$.data.feedbackContent").value("좋았습니다"));
    }

    @Test
    @DisplayName("feedbackContent 없이 요청 → 200 OK")
    void shouldReturn200_WhenFeedbackContentMissing() throws Exception {
        given(feedbackService.saveFeedback(any(), anyLong()))
                .willReturn(new SatisfactionFeedbackResponse(2L, Instant.now(), null));

        mockMvc.perform(post("/api/feedback/satisfaction")
                        .with(authentication(AUTH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "analysisId": 1,
                                  "feedbackType": "CAREER_CONSULTATION",
                                  "satisfactionStatus": "DISSATISFIED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.feedbackId").value(2));
    }

    @Test
    @DisplayName("sajuResultId 누락 → 400 Bad Request")
    void shouldReturn400_WhenSajuResultIdMissing() throws Exception {
        mockMvc.perform(post("/api/feedback/satisfaction")
                        .with(authentication(AUTH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackType": "SAJU",
                                  "satisfactionStatus": "SATISFIED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("feedbackType 누락 → 400 Bad Request")
    void shouldReturn400_WhenFeedbackTypeMissing() throws Exception {
        mockMvc.perform(post("/api/feedback/satisfaction")
                        .with(authentication(AUTH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "analysisId": 1,
                                  "satisfactionStatus": "SATISFIED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("satisfactionStatus 누락 → 400 Bad Request")
    void shouldReturn400_WhenSatisfactionStatusMissing() throws Exception {
        mockMvc.perform(post("/api/feedback/satisfaction")
                        .with(authentication(AUTH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "analysisId": 1,
                                  "feedbackType": "SAJU"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("존재하지 않는 SajuResult → 404 Not Found")
    void shouldReturn404_WhenSajuResultNotFound() throws Exception {
        given(feedbackService.saveFeedback(any(), anyLong()))
                .willThrow(new SajuResultNotFoundException("해당 SajuResult를 찾을 수 없습니다. id=999"));

        mockMvc.perform(post("/api/feedback/satisfaction")
                        .with(authentication(AUTH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "analysisId": 999,
                                  "feedbackType": "CAREER_CONSULTATION",
                                  "satisfactionStatus": "SATISFIED"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SAJU_RESULT_NOT_FOUND"));
    }

    @Test
    @DisplayName("유효하지 않은 feedbackType → 400 Bad Request")
    void shouldReturn400_WhenInvalidFeedbackType() throws Exception {
        mockMvc.perform(post("/api/feedback/satisfaction")
                        .with(authentication(AUTH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "analysisId": 1,
                                  "feedbackType": "INVALID_TYPE",
                                  "satisfactionStatus": "SATISFIED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
