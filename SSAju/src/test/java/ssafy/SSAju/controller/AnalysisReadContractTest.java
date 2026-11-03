package ssafy.SSAju.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.dto.response.AnalysisDetailResponse;
import ssafy.SSAju.dto.response.MyPageResponse;
import ssafy.SSAju.dto.response.UserAnalysisDto;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.handler.SajuGlobalExceptionHandler;
import ssafy.SSAju.service.AuthService;
import ssafy.SSAju.service.UserService;
import ssafy.SSAju.util.CookieUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 마이페이지 목록·상세의 HTTP 계약 검증(T024).
 *
 * <p>분석 ID는 숫자이며, 없는 ID·타인 ID·유형 불일치는 모두 같은 404다. 숫자가 아닌 ID는 기존
 * 요청 형식 오류(400)로 남는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("마이페이지 조회 계약 테스트")
class AnalysisReadContractTest {

    private static final Long USER_ID = 1L;
    private static final Long ANALYSIS_ID = 42L;

    @Mock private UserService userService;
    @Mock private AuthService authService;
    @Mock private CookieUtil cookieUtil;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(authService, userService, cookieUtil))
                .setControllerAdvice(new SajuGlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("목록의 분석 ID는 숫자이며 기본 페이지는 0/10이다")
    void shouldReturnNumericAnalysisIdInList() throws Exception {
        // Given
        given(userService.getMyPage(eq(USER_ID), eq(null), eq(0), eq(10)))
                .willReturn(new MyPageResponse(
                        new MyPageResponse.UserProfileData(USER_ID, "테스트", "t@test.com",
                                Instant.parse("2026-01-01T00:00:00Z"), null),
                        List.of(new UserAnalysisDto(AnalysisType.SAJU.name(), ANALYSIS_ID, "테스트",
                                LocalDate.of(1995, 3, 20), Instant.parse("2026-11-02T03:00:00Z"))),
                        new MyPageResponse.PaginationInfo(0, 10, 1, 1)));

        // When / Then
        mockMvc.perform(get("/api/mypage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.analyses[0].analysisId").value(ANALYSIS_ID))
                .andExpect(jsonPath("$.data.analyses[0].analysisId").isNumber())
                .andExpect(jsonPath("$.data.pagination.page").value(0))
                .andExpect(jsonPath("$.data.pagination.size").value(10));
    }

    @Test
    @DisplayName("상세는 숫자 분석 ID와 유형을 그대로 전달한다")
    void shouldReturnDetailForNumericId() throws Exception {
        // Given
        given(userService.getAnalysisDetail(USER_ID, ANALYSIS_ID, AnalysisType.SAJU))
                .willReturn(new AnalysisDetailResponse(
                        AnalysisType.SAJU.name(), ANALYSIS_ID, "테스트", LocalDate.of(1995, 3, 20),
                        Instant.parse("2026-11-02T03:00:00Z"), null, null,
                        new AnalysisDetailResponse.CareerFortuneDetail("H1", 72, "근거"),
                        null, null));

        // When / Then
        mockMvc.perform(get("/api/mypage/analyses/{analysisId}", ANALYSIS_ID).param("type", "SAJU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(ANALYSIS_ID))
                .andExpect(jsonPath("$.data.analysisId").isNumber())
                .andExpect(jsonPath("$.data.careerFortuneDetail.favoredPeriod").value("H1"));
    }

    @Test
    @DisplayName("없는 ID·타인 ID·유형 불일치는 모두 404다")
    void shouldReturn404ForMissingOtherOwnerAndTypeMismatch() throws Exception {
        // Given
        given(userService.getAnalysisDetail(eq(USER_ID), any(), any()))
                .willThrow(new SajuResultNotFoundException("분석 결과를 찾을 수 없습니다."));

        // When / Then
        for (String type : List.of("SAJU", "CAREER_CONSULTATION", "COMPANY_COMPATIBILITY")) {
            mockMvc.perform(get("/api/mypage/analyses/{analysisId}", 999_999L).param("type", type))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Test
    @DisplayName("숫자가 아닌 분석 ID는 기존 요청 형식 오류(400)로 처리한다")
    void shouldReturn400ForNonNumericId() throws Exception {
        mockMvc.perform(get("/api/mypage/analyses/{analysisId}", "not-a-number").param("type", "SAJU"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("알 수 없는 유형 파라미터도 400으로 거부한다")
    void shouldReturn400ForUnknownType() throws Exception {
        mockMvc.perform(get("/api/mypage/analyses/{analysisId}", ANALYSIS_ID).param("type", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("유형 파라미터가 없으면 400으로 거부한다")
    void shouldReturn400WhenTypeMissing() throws Exception {
        mockMvc.perform(get("/api/mypage/analyses/{analysisId}", ANALYSIS_ID))
                .andExpect(status().isBadRequest());
    }
}
