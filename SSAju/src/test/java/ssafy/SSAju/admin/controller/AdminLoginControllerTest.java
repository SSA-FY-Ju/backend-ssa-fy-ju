package ssafy.SSAju.admin.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceView;
import org.springframework.web.servlet.view.RedirectView;
import ssafy.SSAju.admin.service.AdminAuthenticationService;
import ssafy.SSAju.dto.response.AuthTokenPair;
import ssafy.SSAju.exception.AuthException;
import ssafy.SSAju.service.AuthService;
import ssafy.SSAju.util.JwtUtil;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminLoginController(adminAuthenticationService, authService, jwtUtil))
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
                .andExpect(cookie().path("admin_access_token", "/admin"))
                .andExpect(cookie().exists("admin_refresh_token"))
                .andExpect(cookie().httpOnly("admin_refresh_token", true))
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
    @DisplayName("POST /admin/logout - 인증된 사용자 → authService.logout 호출 후 login 리다이렉트")
    void logout_authenticatedUser_callsLogoutAndRedirects() throws Exception {
        // Given
        var auth = new UsernamePasswordAuthenticationToken(1L, null, java.util.List.of());
        willDoNothing().given(authService).logout(any(), any());

        // When & Then
        mockMvc.perform(post("/admin/logout").principal(auth))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        verify(authService).logout(any(), any());
    }

    @Test
    @DisplayName("POST /admin/logout - 인증 없음 → authService.logout 미호출, login 리다이렉트")
    void logout_unauthenticated_skipsLogoutAndRedirects() throws Exception {
        // When & Then (principal 없음 → authentication = null)
        mockMvc.perform(post("/admin/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        verify(authService, never()).logout(any(), any());
    }

    @Test
    @DisplayName("POST /admin/logout - refresh token 쿠키 없음 → authService.logout(userId, null) 호출")
    void logout_withoutRefreshTokenCookie_callsLogoutWithNull() throws Exception {
        // Given (refresh token 쿠키 미전송)
        var auth = new UsernamePasswordAuthenticationToken(1L, null, java.util.List.of());
        willDoNothing().given(authService).logout(any(), isNull());

        // When & Then
        mockMvc.perform(post("/admin/logout").principal(auth))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        verify(authService).logout(1L, null);
    }
}
