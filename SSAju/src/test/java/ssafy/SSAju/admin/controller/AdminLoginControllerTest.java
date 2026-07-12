package ssafy.SSAju.admin.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceView;
import org.springframework.web.servlet.view.RedirectView;
import ssafy.SSAju.admin.service.AdminAuthenticationService;
import ssafy.SSAju.dto.response.AuthTokenPair;
import ssafy.SSAju.exception.AuthException;
import ssafy.SSAju.service.AuthService;
import ssafy.SSAju.util.JwtUtil;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminLoginController HTTP 레이어 테스트")
class AdminLoginControllerTest {

    @Mock
    private AdminAuthenticationService adminAuthenticationService;

    @Mock
    private AuthService authService;

    @Mock
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminLoginController controller = new AdminLoginController(adminAuthenticationService, authService, jwtUtil);
        // standalone 생성 시 @Value 주입이 안 되므로 운영 기본값(true) 명시 주입
        ReflectionTestUtils.setField(controller, "cookieSecure", true);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setViewResolvers((viewName, locale) -> {
                    if (viewName.startsWith("redirect:")) {
                        return new RedirectView(viewName.substring("redirect:".length()));
                    }
                    return new InternalResourceView(viewName);
                })
                .build();
    }

    @Test
    @DisplayName("GET /admin/login → 로그인 폼 렌더링")
    void loginForm_returnsLoginView() throws Exception {
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/login"))
                .andExpect(model().attributeExists("loginRequest"));
    }

    @Test
    @DisplayName("POST /admin/login - ADMIN 자격증명 → admin_access_token 쿠키 설정 후 dashboard 리다이렉트")
    void login_validAdminCredentials_setCookieAndRedirectToDashboard() throws Exception {
        // Given
        willDoNothing().given(adminAuthenticationService).validateAdminCredentials(anyString(), anyString());
        given(authService.login(any(), anyString()))
                .willReturn(new AuthTokenPair("access-token", "refresh-token", 3600L));
        given(jwtUtil.getAccessTokenExpirationSeconds()).willReturn(3600L);
        given(jwtUtil.getRefreshTokenExpirationSeconds()).willReturn(7200L);

        // When & Then
        mockMvc.perform(post("/admin/login")
                        .param("email", "admin@test.com")
                        .param("password", "password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"))
                .andExpect(cookie().exists("admin_access_token"))
                .andExpect(cookie().httpOnly("admin_access_token", true))
                .andExpect(cookie().secure("admin_access_token", true))
                .andExpect(cookie().sameSite("admin_access_token", "Strict"))
                .andExpect(cookie().path("admin_access_token", "/admin"))
                .andExpect(cookie().exists("admin_refresh_token"))
                .andExpect(cookie().httpOnly("admin_refresh_token", true))
                .andExpect(cookie().secure("admin_refresh_token", true))
                .andExpect(cookie().sameSite("admin_refresh_token", "Strict"))
                .andExpect(cookie().path("admin_refresh_token", "/admin"));
    }

    @Test
    @DisplayName("POST /admin/login - USER 권한 → 에러 메시지와 함께 login 뷰 반환")
    void login_userRoleCredentials_returnsLoginWithError() throws Exception {
        // Given
        willThrow(new AuthException("접근 권한이 없습니다."))
                .given(adminAuthenticationService).validateAdminCredentials(anyString(), anyString());

        // When & Then
        mockMvc.perform(post("/admin/login")
                        .param("email", "user@test.com")
                        .param("password", "password"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/login"))
                .andExpect(model().attribute("errorMessage", "접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("POST /admin/login - 잘못된 자격증명 → 에러 메시지와 함께 login 뷰 반환")
    void login_invalidCredentials_returnsLoginWithError() throws Exception {
        // Given
        willThrow(new AuthException("이메일 또는 비밀번호가 일치하지 않습니다."))
                .given(adminAuthenticationService).validateAdminCredentials(anyString(), anyString());

        // When & Then
        mockMvc.perform(post("/admin/login")
                        .param("email", "admin@test.com")
                        .param("password", "wrong"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/login"))
                .andExpect(model().attribute("errorMessage", "이메일 또는 비밀번호가 일치하지 않습니다."));
    }

    @Test
    @DisplayName("POST /admin/logout - refresh token 쿠키 있음 → authService.logout(userId, token) 호출 후 login 리다이렉트")
    void logout_withRefreshTokenCookie_callsLogoutAndRedirects() throws Exception {
        // Given
        given(jwtUtil.extractUserId("valid-refresh-token")).willReturn(1L);
        willDoNothing().given(authService).logout(any(), any(), any());

        // When & Then
        mockMvc.perform(post("/admin/logout")
                        .cookie(new Cookie("admin_refresh_token", "valid-refresh-token")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"))
                .andExpect(cookie().maxAge("admin_access_token", 0))
                .andExpect(cookie().maxAge("admin_refresh_token", 0))
                .andExpect(cookie().secure("admin_access_token", true))
                .andExpect(cookie().sameSite("admin_access_token", "Strict"));

        verify(authService).logout(eq(1L), eq("valid-refresh-token"), isNull());
    }

    @Test
    @DisplayName("POST /admin/logout - refresh token 쿠키 없음 → authService.logout 미호출, login 리다이렉트")
    void logout_withoutRefreshTokenCookie_skipsLogoutAndRedirects() throws Exception {
        // When & Then (refresh token 쿠키 미전송)
        mockMvc.perform(post("/admin/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"))
                .andExpect(cookie().maxAge("admin_access_token", 0))
                .andExpect(cookie().maxAge("admin_refresh_token", 0));

        verify(authService, never()).logout(any(), any(), any());
    }

    @Test
    @DisplayName("POST /admin/logout - access token 만료 상태에서도 refresh token으로 서버 폐기 수행")
    void logout_withExpiredAccessToken_stillRevokesViaRefreshToken() throws Exception {
        // Given: access token이 없어도 refresh token만으로 로그아웃 가능
        given(jwtUtil.extractUserId("refresh-only-token")).willReturn(2L);
        willDoNothing().given(authService).logout(any(), any(), any());

        // When & Then (access token 쿠키 없이 refresh token만 전송)
        mockMvc.perform(post("/admin/logout")
                        .cookie(new Cookie("admin_refresh_token", "refresh-only-token")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"))
                .andExpect(cookie().maxAge("admin_access_token", 0))
                .andExpect(cookie().maxAge("admin_refresh_token", 0));

        verify(authService).logout(eq(2L), eq("refresh-only-token"), isNull());
    }
}
