package ssafy.SSAju.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ssafy.SSAju.admin.config.AdminCookieJwtFilter;
import ssafy.SSAju.admin.dto.AdminLoginRequestDTO;
import ssafy.SSAju.admin.service.AdminAuthenticationService;
import ssafy.SSAju.dto.request.LoginRequest;
import ssafy.SSAju.dto.response.AuthTokenPair;
import ssafy.SSAju.exception.AuthException;
import ssafy.SSAju.service.AuthService;
import ssafy.SSAju.util.ClientIpUtil;
import ssafy.SSAju.util.JwtUtil;

import java.util.Arrays;

@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminLoginController {

    private final AdminAuthenticationService adminAuthenticationService;
    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @Value("${server.cookie.secure:true}")
    private boolean cookieSecure;

    @GetMapping("/login")
    public String loginForm(Model model) {
        model.addAttribute("loginRequest", new AdminLoginRequestDTO("", ""));
        return "admin/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute AdminLoginRequestDTO loginRequest,
                        BindingResult bindingResult,
                        Model model,
                        HttpServletRequest request,
                        HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("loginRequest", loginRequest);
            return "admin/login";
        }

        try {
            adminAuthenticationService.validateAdminCredentials(loginRequest.email(), loginRequest.password());

            LoginRequest authRequest = new LoginRequest(loginRequest.email(), loginRequest.password());
            String clientIp = ClientIpUtil.getClientIp(request);
            AuthTokenPair tokenPair = authService.login(authRequest, clientIp);

            response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(
                    AdminCookieJwtFilter.ADMIN_TOKEN_COOKIE,
                    tokenPair.accessToken(),
                    "/admin",
                    jwtUtil.getAccessTokenExpirationSeconds()
            ).toString());

            response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(
                    AdminCookieJwtFilter.ADMIN_REFRESH_TOKEN_COOKIE,
                    tokenPair.refreshTokenValue(),
                    "/admin",
                    jwtUtil.getRefreshTokenExpirationSeconds()
            ).toString());

            log.info("관리자 로그인 완료");
            return "redirect:/admin/dashboard";

        } catch (AuthException e) {
            model.addAttribute("loginRequest", loginRequest);
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookieValue(request, AdminCookieJwtFilter.ADMIN_REFRESH_TOKEN_COOKIE);
        if (refreshToken != null) {
            try {
                Long userId = jwtUtil.extractUserId(refreshToken);
                if (userId != null) {
                    authService.logout(userId, refreshToken);
                }
            } catch (Exception e) {
                log.warn("로그아웃 토큰 처리 실패: {}", e.getMessage());
            }
        }

        response.addHeader(HttpHeaders.SET_COOKIE, expireCookie(
                AdminCookieJwtFilter.ADMIN_TOKEN_COOKIE, "/admin").toString());
        response.addHeader(HttpHeaders.SET_COOKIE, expireCookie(
                AdminCookieJwtFilter.ADMIN_REFRESH_TOKEN_COOKIE, "/admin").toString());

        return "redirect:/admin/login";
    }

    private ResponseCookie buildCookie(String name, String value, String path, long maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path(path)
                .maxAge(maxAge)
                .sameSite("Strict")
                .build();
    }

    private ResponseCookie expireCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path(path)
                .maxAge(0)
                .sameSite("Strict")
                .build();
    }

    private String extractCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(c -> c.getValue())
                .findFirst()
                .orElse(null);
    }
}
