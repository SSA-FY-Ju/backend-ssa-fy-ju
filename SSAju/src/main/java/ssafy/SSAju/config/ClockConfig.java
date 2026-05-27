package ssafy.SSAju.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 애플리케이션 전역 {@link Clock} 빈 설정.
 *
 * <p>서비스에서 {@code YearMonth.now()} 대신 {@code YearMonth.now(clock)}을 사용하면
 * 타임존이 "Asia/Seoul"로 고정되어 서버 간 타임존 차이 문제를 방지합니다.
 *
 * <p><b>테스트 편의성:</b> 단위 테스트에서 {@code Clock.fixed(instant, zone)}을 주입하면
 * 날짜 의존 로직을 월 변경 없이 안정적으로 테스트할 수 있습니다.
 */
@Configuration
public class ClockConfig {

    /** 서비스 기준 타임존: 한국 표준시(KST, UTC+9) */
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(SERVICE_ZONE);
    }
}
