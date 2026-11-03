package ssafy.SSAju.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import ssafy.SSAju.career.converter.CareerJsonObjectMapperSupport;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 통합 분석 구조(manse/saju) 테스트가 공유하는 컨테이너 설정·고정 시각·합성 자료 헬퍼.
 *
 * <p>기존 {@code CareerResultJsonStorageIntegrationTest}의 MySQL 설정과
 * {@code concurrency/RedisTestSupport}의 Redis 설정 패턴을 따른다. 컨테이너 인스턴스는 공유하지
 * 않고 각 테스트 클래스가 자기 컨테이너를 만든다 — 공유하면 한 클래스가 남긴 데이터나 정리 중인
 * 스레드가 다른 클래스의 실행과 부딪힌다.
 *
 * <p>Redis 설정은 Spring Data Redis({@code spring.data.redis.*})만 등록한다. RedissonClient를
 * 직접 초기화하는 패턴은 복사하지 않는다 — 분산락 제거 후 남길 인증용 Redis는 기본 Lettuce
 * 연결로 동작해야 한다.
 *
 * <p>합성 자료만 사용하며 실제 개인정보나 운영 DB 원본을 넣지 않는다.
 */
public final class UnifiedAnalysisTestSupport {

    /** 서비스 기준 타임존 (ClockConfig.SERVICE_ZONE과 동일). */
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** 고정 분석 시각: KST 2026-11-02 12:00. 월·연 경계 재계산 여부를 확인하기 좋은 값이다. */
    public static final Instant FIXED_INSTANT = Instant.parse("2026-11-02T03:00:00Z");

    private static final String FIXTURE_DIRECTORY = "/analysis/";

    public static final String MANSE_DATA_FIXTURE = "manse-data.json";
    public static final String SAJU_RESULT_FIXTURE = "saju-result.json";
    public static final String CONSULTATION_RESULT_FIXTURE = "consultation-result.json";
    public static final String COMPATIBILITY_RESULT_FIXTURE = "compatibility-result.json";
    public static final String COMPATIBILITY_INPUT_FIXTURE = "compatibility-input.json";

    private UnifiedAnalysisTestSupport() {
    }

    public static Clock fixedClock() {
        return Clock.fixed(FIXED_INSTANT, SERVICE_ZONE);
    }

    public static MySQLContainer<?> newMySqlContainer() {
        return new MySQLContainer<>("mysql:8.0");
    }

    public static void registerMySqlProperties(DynamicPropertyRegistry registry, MySQLContainer<?> mysql) {
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
    }

    public static GenericContainer<?> newRedisContainer() {
        return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    }

    public static void registerRedisProperties(DynamicPropertyRegistry registry, GenericContainer<?> redis) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    /** 합성 fixture 파일을 원문 그대로 읽는다. */
    public static String readFixture(String fileName) {
        try (InputStream in = UnifiedAnalysisTestSupport.class
                .getResourceAsStream(FIXTURE_DIRECTORY + fileName)) {
            if (in == null) {
                throw new IllegalStateException("테스트 fixture를 찾을 수 없습니다: " + fileName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 합성 만세력 fixture를 {@link ManseData}로 읽는다. */
    public static ManseData manseData() {
        return CareerJsonObjectMapperSupport.mapper()
                .readValue(readFixture(MANSE_DATA_FIXTURE), ManseData.class);
    }

    public static User newUser(String email) {
        return User.builder()
                .email(email)
                .passwordHash("test-hash")
                .name("테스트 사용자")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(FIXED_INSTANT)
                .privacyAgreedAt(FIXED_INSTANT)
                .build();
    }

    public static UserProfile newProfile(LocalDate birthDate, LocalTime birthTime) {
        return UserProfile.builder()
                .birthDate(birthDate)
                .birthTime(birthTime)
                .build();
    }
}
