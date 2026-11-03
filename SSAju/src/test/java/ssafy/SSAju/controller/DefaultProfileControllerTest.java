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
import ssafy.SSAju.dto.request.DefaultProfileRequest;
import ssafy.SSAju.dto.response.DefaultProfileResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.exception.UnauthorizedException;
import ssafy.SSAju.handler.SajuGlobalExceptionHandler;
import ssafy.SSAju.service.AuthService;
import ssafy.SSAju.service.DefaultProfileService;
import ssafy.SSAju.service.UserService;
import ssafy.SSAju.util.CookieUtil;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 기본 출생정보 조회·저장 API의 HTTP 계약 검증(T045).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("기본 출생정보 API 계약 테스트")
class DefaultProfileControllerTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);
    private static final String VALID_BODY = """
            {"birthDate": "1995-03-20", "birthTime": "09:15"}
            """;

    @Mock private DefaultProfileService defaultProfileService;
    @Mock private AuthService authService;
    @Mock private UserService userService;
    @Mock private CookieUtil cookieUtil;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(authService, userService, defaultProfileService, cookieUtil))
                .setControllerAdvice(new SajuGlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("기본값이 없으면 200과 함께 data=null을 돌려준다")
    void shouldReturnNullDataWhenDefaultProfileIsUnset() throws Exception {
        // Given
        authenticate();
        given(defaultProfileService.getDefaultProfile(USER_ID)).willReturn(null);

        // When / Then
        mockMvc.perform(get("/api/users/me/default-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("기본값이 있으면 출생정보를 돌려준다")
    void shouldReturnStoredDefaultProfile() throws Exception {
        // Given
        authenticate();
        given(defaultProfileService.getDefaultProfile(USER_ID))
                .willReturn(new DefaultProfileResponse(BIRTH_DATE, BIRTH_TIME));

        // When / Then
        mockMvc.perform(get("/api/users/me/default-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birthDate").value("1995-03-20"))
                .andExpect(jsonPath("$.data.birthTime").value("09:15:00"));
    }

    @Test
    @DisplayName("저장 요청은 인증 사용자와 출생정보만으로 처리한다")
    void shouldSaveDefaultProfileForAuthenticatedUser() throws Exception {
        // Given
        authenticate();
        given(defaultProfileService.changeDefaultProfile(eq(USER_ID), any(DefaultProfileRequest.class)))
                .willReturn(new DefaultProfileResponse(BIRTH_DATE, BIRTH_TIME));

        // When / Then
        mockMvc.perform(put("/api/users/me/default-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.birthDate").value("1995-03-20"));
        verify(defaultProfileService).changeDefaultProfile(eq(USER_ID), any(DefaultProfileRequest.class));
    }

    @Test
    @DisplayName("출생정보가 빠진 저장 요청은 400으로 거부한다")
    void shouldRejectRequestWithoutBirthInfo() throws Exception {
        // Given
        authenticate();

        // When / Then
        mockMvc.perform(put("/api/users/me/default-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1995-03-20"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verify(defaultProfileService, never()).changeDefaultProfile(any(), any());
    }

    @Test
    @DisplayName("지원 범위를 벗어난 출생정보는 기존 검증 정책대로 거부한다")
    void shouldRejectUnsupportedBirthDate() throws Exception {
        // Given
        authenticate();
        given(defaultProfileService.changeDefaultProfile(eq(USER_ID), any()))
                .willThrow(new InvalidSajuDataException("1900년 이전 생년월일은 지원하지 않습니다."));

        // When / Then
        mockMvc.perform(put("/api/users/me/default-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1899-12-31", "birthTime": "09:15"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("미인증 저장 요청은 기존 정책대로 거부한다")
    void shouldRejectUnauthenticatedSave() throws Exception {
        // Given: 인증 주체 없음
        given(defaultProfileService.changeDefaultProfile(eq(null), any()))
                .willThrow(new UnauthorizedException("인증 정보가 없습니다. 로그인 후 시도해주세요."));

        // When / Then
        mockMvc.perform(put("/api/users/me/default-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }
}
