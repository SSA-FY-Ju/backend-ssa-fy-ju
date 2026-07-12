package ssafy.SSAju.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * {@code @CreatedDate}/{@code @LastModifiedDate}가 시스템 시간이 아닌 애플리케이션 전역
 * {@link ClockConfig#clock() Clock} 빈 기준으로 기록되도록 커스텀 {@link DateTimeProvider}를 연결한다.
 *
 * <p>테스트에서 {@code Clock.fixed(...)}를 주입하면 엔티티 타임스탬프도 결정론적으로 검증할 수 있다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
