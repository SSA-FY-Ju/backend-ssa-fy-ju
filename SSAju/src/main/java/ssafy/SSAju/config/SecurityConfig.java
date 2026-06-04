package ssafy.SSAju.config;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ssafy.SSAju.filter.JwtAuthenticationFilter;
import ssafy.SSAju.filter.TokenValidationFilter;
import ssafy.SSAju.repository.RefreshTokenRepository;
import ssafy.SSAju.security.JwtAccessDeniedHandler;
import ssafy.SSAju.security.JwtAuthenticationEntryPoint;
import ssafy.SSAju.security.JwtExceptionFilter;
import ssafy.SSAju.util.JwtUtil;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${springdoc.swagger-ui.path}")
    private String swaggerUiPath;

    @Value("${cors.allowed-origins}")
    private String corsAllowedOrigins;

    @Bean
    public JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new JwtAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public JwtAccessDeniedHandler jwtAccessDeniedHandler(ObjectMapper objectMapper) {
        return new JwtAccessDeniedHandler(objectMapper);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtUtil jwtUtil,
                                           JwtAuthenticationEntryPoint entryPoint,
                                           JwtAccessDeniedHandler accessDeniedHandler,
                                           ObjectMapper objectMapper,
                                           RefreshTokenRepository refreshTokenRepository) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers("/swagger-ui/**", swaggerUiPath, "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                    .requestMatchers("/api/auth/check-email", "/api/auth/signup", "/api/auth/login", "/api/auth/refresh").permitAll()
                    .requestMatchers("/api/auth/**").authenticated()
                    .requestMatchers("/api/career/**", "/api/feedback/**", "/api/company/**").authenticated()
                    .requestMatchers("/api/mypage/**", "/api/users/**").authenticated()
                    .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .addFilterBefore(new JwtAuthenticationFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtExceptionFilter(objectMapper), JwtAuthenticationFilter.class)
            // RefreshToken 검증 필터: /api/auth/refresh 엔드포인트 요청 시 Refresh-Token 헤더 유효성 검증
            .addFilterAfter(new TokenValidationFilter(refreshTokenRepository, objectMapper), JwtAuthenticationFilter.class)
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Refresh-Token"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
