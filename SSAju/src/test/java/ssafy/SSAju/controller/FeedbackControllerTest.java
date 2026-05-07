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
import ssafy.SSAju.dto.response.SatisfactionFeedbackResponse;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.handler.SajuGlobalExceptionHandler;
import ssafy.SSAju.service.FeedbackService;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackController HTTP 레이어 테스트")
class FeedbackControllerTest {

    @Mock
    private FeedbackService feedbackService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FeedbackController(feedbackService))
                .setControllerAdvice(new SajuGlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("유효한 피드백 요청 → 200 OK + feedbackId 반환")
    void shouldReturn200_WhenValidRequest() throws Exception {
        given(feedbackService.saveFeedback(any()))
                .willReturn(new SatisfactionFeedbackResponse(1L, LocalDateTime.now(), "좋았습니다"));

        mockMvc.perform(post("/api/feedback/satisfaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sajuResultId": 1,
                                  "feedbackType": "CAREER_TIMING",
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
        given(feedbackService.saveFeedback(any()))
                .willReturn(new SatisfactionFeedbackResponse(2L, LocalDateTime.now(), null));

        mockMvc.perform(post("/api/feedback/satisfaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sajuResultId": 1,
                                  "feedbackType": "CONSULTATION",
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackType": "CAREER_TIMING",
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sajuResultId": 1,
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sajuResultId": 1,
                                  "feedbackType": "CAREER_TIMING"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("존재하지 않는 SajuResult → 404 Not Found")
    void shouldReturn404_WhenSajuResultNotFound() throws Exception {
        given(feedbackService.saveFeedback(any()))
                .willThrow(new SajuResultNotFoundException("해당 SajuResult를 찾을 수 없습니다. id=999"));

        mockMvc.perform(post("/api/feedback/satisfaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sajuResultId": 999,
                                  "feedbackType": "CAREER_TIMING",
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sajuResultId": 1,
                                  "feedbackType": "INVALID_TYPE",
                                  "satisfactionStatus": "SATISFIED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
