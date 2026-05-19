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
import ssafy.SSAju.dto.response.CareerTimingResponse;
import ssafy.SSAju.handler.SajuGlobalExceptionHandler;
import ssafy.SSAju.service.CareerFortuneService;
import ssafy.SSAju.service.UserService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CareerTimingController HTTP 레이어 테스트")
class CareerTimingControllerTest {

    @Mock
    private CareerFortuneService careerFortuneService;

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CareerTimingController(careerFortuneService, userService))
                .setControllerAdvice(new SajuGlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
    @DisplayName("인증된 사용자 요청 → associateSajuResultWithUser 호출됨")
    void shouldAssociateUser_WhenAuthenticated() throws Exception {
        Long userId = 1L;
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));

        given(careerFortuneService.analyzeCareerTiming(any(LocalDate.class), any(LocalTime.class)))
                .willReturn(new CareerTimingResponse("H1", 75, "상반기가 유리합니다."));

        mockMvc.perform(post("/api/career/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1990-10-10", "birthTime": "14:30"}
                                """))
                .andExpect(status().isOk());

        verify(userService).associateSajuResultWithUser(
                eq(userId), any(LocalDate.class), any(LocalTime.class));
    }

    @Test
    @DisplayName("비인증 요청 → associateSajuResultWithUser 호출 안 됨")
    void shouldNotAssociateUser_WhenNotAuthenticated() throws Exception {
        given(careerFortuneService.analyzeCareerTiming(any(LocalDate.class), any(LocalTime.class)))
                .willReturn(new CareerTimingResponse("H2", 60, "하반기가 유리합니다."));

        mockMvc.perform(post("/api/career/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1990-10-10", "birthTime": "14:30"}
                                """))
                .andExpect(status().isOk());

        verify(userService, never()).associateSajuResultWithUser(any(), any(), any());
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
