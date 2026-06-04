package ssafy.SSAju.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 설정.
 *
 * <p>일일 API 한도 체크는 인터셉터 방식에서 서비스 계층으로 이동되었습니다.
 * 캐시 히트 시 무료 처리를 위해 각 서비스(CareerFortuneService, ConsultationService,
 * CompanyMatchingService)에서 OpenAI/FastAPI 호출 직전에 직접 차감합니다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    // 인터셉터 기반 한도 체크 제거: 서비스 레이어에서 캐시 히트 여부 판단 후 차감
}
