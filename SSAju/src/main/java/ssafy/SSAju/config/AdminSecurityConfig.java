package ssafy.SSAju.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import ssafy.SSAju.admin.config.AdminAccessDeniedHandler;
import ssafy.SSAju.admin.config.AdminAuthenticationEntryPoint;
import ssafy.SSAju.admin.config.AdminCookieJwtFilter;
import ssafy.SSAju.security.JwtExceptionFilter;
import ssafy.SSAju.util.JwtUtil;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Configuration
@Order(0)
@EnableMethodSecurity
@RequiredArgsConstructor
public class AdminSecurityConfig {

    private final AdminAuthenticationEntryPoint adminAuthenticationEntryPoint;
    private final AdminAccessDeniedHandler adminAccessDeniedHandler;

    @Value("${server.cookie.secure:true}")
    private boolean cookieSecure;

    @Bean
    public SecurityFilterChain adminFilterChain(HttpSecurity http, JwtUtil jwtUtil,
                                                ObjectMapper objectMapper) throws Exception {
        AdminCookieJwtFilter cookieJwtFilter = new AdminCookieJwtFilter(jwtUtil, cookieSecure);

        http
            .securityMatcher("/admin/**")
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/admin/daily-usages/**"))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers("/admin/login", "/admin/logout").permitAll()
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(adminAuthenticationEntryPoint)
                .accessDeniedHandler(adminAccessDeniedHandler))
            // JwtAuthenticationFilter 대신 쿠키도 지원하는 AdminCookieJwtFilter 사용
            .addFilterBefore(cookieJwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtExceptionFilter(objectMapper), AdminCookieJwtFilter.class)
            // Spring Security lazy CSRF token 문제 해결: 모든 요청에서 토큰을 강제 로딩해 쿠키를 응답에 담음
            .addFilterAfter(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class)
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    // Spring Security 7의 lazy CSRF token 로딩 문제를 해결하는 필터.
    // CsrfFilter가 deferred token을 설정한 뒤 이 필터가 getToken()을 호출해
    // 실제 토큰을 로딩하고 XSRF-TOKEN 쿠키를 모든 응답에 포함시킨다.
    private static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
