package ssafy.SSAju.admin.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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

@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminLoginController {

    private final AdminAuthenticationService adminAuthenticationService;
    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @GetMapping("/login")
    public String loginForm(Model model) {
        model.addAttribute("loginRequest", new AdminLoginRequestDTO("", ""));
        return "admin/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute AdminLoginRequestDTO loginRequest,
                        Model model,
                        HttpServletRequest request,
                        HttpServletResponse response) {
        try {
            adminAuthenticationService.validateAdminCredentials(loginRequest.email(), loginRequest.password());

            LoginRequest authRequest = new LoginRequest(loginRequest.email(), loginRequest.password());
            String clientIp = ClientIpUtil.getClientIp(request);
            AuthTokenPair tokenPair = authService.login(authRequest, clientIp);

            // SSR 페이지에서 사용할 HttpOnly 쿠키에 AccessToken 저장
            Cookie adminCookie = new Cookie(AdminCookieJwtFilter.ADMIN_TOKEN_COOKIE, tokenPair.accessToken());
            adminCookie.setHttpOnly(true);
            adminCookie.setPath("/admin");
            adminCookie.setMaxAge((int) jwtUtil.getAccessTokenExpirationSeconds());
            response.addCookie(adminCookie);

            log.info("관리자 로그인 완료: email={}", loginRequest.email());
            return "redirect:/admin/dashboard";

        } catch (AuthException e) {
            model.addAttribute("loginRequest", loginRequest);
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/login";
        }
    }

    @PostMapping("/logout")
    public String logout(Authentication authentication,
                         @RequestHeader(value = "Refresh-Token", required = false) String refreshToken,
                         HttpServletResponse response) {
        if (authentication != null) {
            Long userId = (Long) authentication.getPrincipal();
            authService.logout(userId, refreshToken);
        }

        // 쿠키 삭제
        Cookie expired = new Cookie(AdminCookieJwtFilter.ADMIN_TOKEN_COOKIE, "");
        expired.setMaxAge(0);
        expired.setPath("/admin");
        expired.setHttpOnly(true);
        response.addCookie(expired);

        return "redirect:/admin/login";
    }
}
