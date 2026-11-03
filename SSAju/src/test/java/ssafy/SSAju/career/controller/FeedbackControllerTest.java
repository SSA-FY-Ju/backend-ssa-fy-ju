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
import ssafy.SSAju.exception.FeedbackAlreadyExistsException;
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

/**
 * 피드백 작성 API 계약(T054). 요청 필드는 analysisId(숫자)·satisfactionStatus·feedbackContent이며
 * 분석 유형은 받지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("FeedbackController HTTP 레이어 테스트")
class FeedbackControllerTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private FeedbackService feedbackService;

    private MockMvc mockMvc;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken(1L, null, List.of());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

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
                                  "satisfactionStatus": "DISSATISFIED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedbackId").value(2));
    }

    @Test
    @DisplayName("analysisId 누락 → 400 Bad Request")
    void shouldReturn400_WhenAnalysisIdMissing() throws Exception {
        mockMvc.perform(post("/api/feedback/satisfaction")
                        .with(authentication(AUTH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
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
                                  "analysisId": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("숫자가 아닌 analysisId → 400 Bad Request")
    void shouldReturn400_WhenAnalysisIdIsNotNumeric() throws Exception {
        mockMvc.perform(post("/api/feedback/satisfaction")
                        .with(authentication(AUTH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "analysisId": "not-a-number",
                                  "satisfactionStatus": "SATISFIED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("없는 분석·타인 분석·관운 분석 대상은 모두 404")
    void shouldReturn404_WhenAnalysisIsNotAvailableForFeedback() throws Exception {
        given(feedbackService.saveFeedback(any(), anyLong()))
                .willThrow(new SajuResultNotFoundException("분석 결과를 찾을 수 없습니다."));

        mockMvc.perform(post("/api/feedback/satisfaction")
                        .with(authentication(AUTH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "analysisId": 999,
                                  "satisfactionStatus": "SATISFIED"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SAJU_RESULT_NOT_FOUND"));
    }

    @Test
    @DisplayName("같은 분석에 중복 제출 → 409 Conflict")
    void shouldReturn409_WhenDuplicateFeedback() throws Exception {
        given(feedbackService.saveFeedback(any(), anyLong()))
                .willThrow(new FeedbackAlreadyExistsException("이미 이 분석에 대한 피드백을 작성했습니다."));

        mockMvc.perform(post("/api/feedback/satisfaction")
                        .with(authentication(AUTH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "analysisId": 1,
                                  "satisfactionStatus": "SATISFIED"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FEEDBACK_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("예전 클라이언트가 보낸 feedbackType은 무시한다")
    void shouldIgnoreLegacyFeedbackTypeField() throws Exception {
        given(feedbackService.saveFeedback(any(), anyLong()))
                .willReturn(new SatisfactionFeedbackResponse(3L, Instant.now(), null));

        mockMvc.perform(post("/api/feedback/satisfaction")
                        .with(authentication(AUTH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "analysisId": 1,
                                  "feedbackType": "CAREER_CONSULTATION",
                                  "satisfactionStatus": "SATISFIED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedbackId").value(3));
    }
}
