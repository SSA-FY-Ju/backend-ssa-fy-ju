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
import ssafy.SSAju.dto.response.CareerTimingResponse;
import ssafy.SSAju.handler.SajuGlobalExceptionHandler;
import ssafy.SSAju.service.CareerFortuneService;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CareerTimingController HTTP 레이어 테스트")
class CareerTimingControllerTest {

    @Mock
    private CareerFortuneService careerFortuneService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CareerTimingController(careerFortuneService))
                .setControllerAdvice(new SajuGlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("유효한 birthDate + birthTime → 200 OK + H1/H2 응답")
    void shouldReturn200_WhenValidRequest() throws Exception {
        given(careerFortuneService.analyzeCareerTiming(any(LocalDate.class), any(LocalTime.class)))
                .willReturn(new CareerTimingResponse("H1", 75, "상반기가 취업에 유리합니다."));

        mockMvc.perform(post("/api/career/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1990-10-10", "birthTime": "14:30"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.favoredPeriod").value("H1"))
                .andExpect(jsonPath("$.data.confidenceScore").value(75))
                .andExpect(jsonPath("$.data.reasoning").isNotEmpty());
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
}
