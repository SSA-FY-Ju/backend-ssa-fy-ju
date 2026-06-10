package ssafy.SSAju.config;

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
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import ssafy.SSAju.admin.config.AdminAccessDeniedHandler;
import ssafy.SSAju.admin.config.AdminAuthenticationEntryPoint;
import ssafy.SSAju.admin.config.AdminCookieJwtFilter;
import ssafy.SSAju.security.JwtExceptionFilter;
import ssafy.SSAju.util.JwtUtil;
import tools.jackson.databind.ObjectMapper;

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
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers("/admin/login").permitAll()
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(adminAuthenticationEntryPoint)
                .accessDeniedHandler(adminAccessDeniedHandler))
            // JwtAuthenticationFilter 대신 쿠키도 지원하는 AdminCookieJwtFilter 사용
            .addFilterBefore(cookieJwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtExceptionFilter(objectMapper), AdminCookieJwtFilter.class)
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
