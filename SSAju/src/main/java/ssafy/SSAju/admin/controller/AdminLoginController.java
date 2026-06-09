package ssafy.SSAju.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
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
import ssafy.SSAju.admin.dto.AdminLoginRequestDTO;
import ssafy.SSAju.admin.dto.AdminLoginResponseDTO;
import ssafy.SSAju.admin.service.AdminAuthenticationService;
import ssafy.SSAju.dto.request.LoginRequest;
import ssafy.SSAju.dto.response.AuthTokenPair;
import ssafy.SSAju.exception.AuthException;
import ssafy.SSAju.service.AuthService;
import ssafy.SSAju.util.ClientIpUtil;

@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminLoginController {

    private final AdminAuthenticationService adminAuthenticationService;
    private final AuthService authService;

    @GetMapping("/login")
    public String loginForm(Model model) {
        model.addAttribute("loginRequest", new AdminLoginRequestDTO("", ""));
        return "admin/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute AdminLoginRequestDTO loginRequest,
                        Model model,
                        HttpServletRequest request) {
        try {
            adminAuthenticationService.validateAdminCredentials(loginRequest.email(), loginRequest.password());

            LoginRequest authRequest = new LoginRequest(loginRequest.email(), loginRequest.password());
            String clientIp = ClientIpUtil.getClientIp(request);
            AuthTokenPair tokenPair = authService.login(authRequest, clientIp);

            model.addAttribute("accessToken", tokenPair.accessToken());
            model.addAttribute("refreshToken", tokenPair.refreshTokenValue());
            model.addAttribute("expiresIn", tokenPair.expiresIn());
            return "admin/login-success";

        } catch (AuthException e) {
            model.addAttribute("loginRequest", loginRequest);
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/login";
        }
    }

    @PostMapping("/logout")
    public String logout(Authentication authentication,
                         @RequestHeader(value = "Refresh-Token", required = false) String refreshToken) {
        if (authentication != null && refreshToken != null) {
            Long userId = (Long) authentication.getPrincipal();
            authService.logout(userId, refreshToken);
        }
        return "redirect:/admin/login";
    }
}
