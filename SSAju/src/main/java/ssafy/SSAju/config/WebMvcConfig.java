package ssafy.SSAju.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ssafy.SSAju.handler.APIUsageInterceptor;
import ssafy.SSAju.service.DailyApiUsageService;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final DailyApiUsageService dailyApiUsageService;

    public WebMvcConfig(DailyApiUsageService dailyApiUsageService) {
        this.dailyApiUsageService = dailyApiUsageService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new APIUsageInterceptor(dailyApiUsageService))
                .addPathPatterns("/api/**");
    }
}
