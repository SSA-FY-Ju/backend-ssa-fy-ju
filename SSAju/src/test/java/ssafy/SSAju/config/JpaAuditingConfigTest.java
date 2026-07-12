package ssafy.SSAju.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.repository.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA Auditing이 시스템 시간이 아닌 주입된 {@link Clock} 기준으로 동작하는지 검증 (T010).
 */
@SpringBootTest
@Transactional
@DisplayName("JPA Auditing Clock 연동 테스트 (T010)")
class JpaAuditingConfigTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2020-01-01T00:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("T010-1: 고정 Clock 주입 시 createdAt이 시스템 현재 시각이 아닌 고정 시각과 일치한다")
    void createdAt_usesInjectedClockInsteadOfSystemTime() {
        // Given
        User user = User.builder()
                .email("clock-test@example.com")
                .passwordHash("hashed-password")
                .name("클록테스트")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(FIXED_INSTANT)
                .privacyAgreedAt(FIXED_INSTANT)
                .build();

        // When
        User saved = userRepository.saveAndFlush(user);

        // Then
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(saved.getCreatedAt()).isNotEqualTo(Instant.now());
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        public Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneId.of("Asia/Seoul"));
        }
    }
}
