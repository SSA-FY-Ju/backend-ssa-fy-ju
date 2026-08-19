package ssafy.SSAju.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.DataAccessException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CompanyCompatibilityJdbcRepository 단위 테스트 (US5, T035).
 *
 * <p>동시 삽입 방어는 더 이상 이 저장소의 책임이 아니다 — userProfile+company+role 단위
 * 분산락({@code CompanyCompatibilityLockedAnalysisService})이 경합 자체를 막는다.
 * 동시성 시나리오는 {@code concurrency/CompanyCompatibilityConcurrencyTest}에서 검증하고,
 * 이 테스트는 저장소 자체의 정상 삽입/중복 위반 시 예외 전파만 검증한다.
 */
@Testcontainers
@SpringBootTest
@DisplayName("CompanyCompatibilityJdbcRepository 단위 테스트")
class CompanyCompatibilityJdbcRepositoryTest {

    private static final int FIXED_COMPATIBILITY_MONTH = 202605;

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Autowired private CompanyCompatibilityJdbcRepository jdbcRepository;
    @Autowired private CompanyCompatibilityRepository compatibilityRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private SajuResultRepository sajuResultRepository;
    @Autowired private CareerFortuneRepository careerFortuneRepository;
    @Autowired private SajuFullDataRepository sajuFullDataRepository;
    @Autowired private UserRepository userRepository;

    private UserProfile savedProfile;
    private User testUser;

    @BeforeEach
    void setUp() {
        compatibilityRepository.deleteAllInBatch();
        careerFortuneRepository.deleteAllInBatch();
        sajuFullDataRepository.deleteAllInBatch();
        sajuResultRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        testUser = userRepository.save(User.builder()
                .email("jdbc-test@test.com")
                .passwordHash("hash")
                .name("테스트")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(Instant.now())
                .privacyAgreedAt(Instant.now())
                .build());

        savedProfile = userProfileRepository.save(
                UserProfile.builder()
                        .birthDate(LocalDate.of(1998, 5, 7))
                        .birthTime(LocalTime.of(14, 30))
                        .build()
        );
    }

    @Test
    @DisplayName("신규 삽입 성공")
    void shouldInsert_WhenNewEntity() {
        // Given
        CompanyCompatibility entity = buildEntity(savedProfile, "현대오토에버", JobCategoryEnum.TECH_BACKEND);

        // When
        jdbcRepository.insert(entity);

        // Then
        assertThat(compatibilityRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("중복 삽입 → 분산락 보호 하에서는 발생해선 안 되는 진짜 위반으로 간주하여 DataAccessException 전파")
    void shouldThrow_WhenDuplicateInsert() {
        // Given: 동일 조합 먼저 삽입
        CompanyCompatibility first = buildEntity(savedProfile, "현대오토에버", JobCategoryEnum.TECH_BACKEND);
        jdbcRepository.insert(first);

        CompanyCompatibility duplicate = buildEntity(savedProfile, "현대오토에버", JobCategoryEnum.TECH_BACKEND);

        // When & Then
        assertThatThrownBy(() -> jdbcRepository.insert(duplicate))
                .isInstanceOf(DataAccessException.class);
        assertThat(compatibilityRepository.count()).isEqualTo(1);
    }

    private CompanyCompatibility buildEntity(UserProfile profile, String companyName,
                                              JobCategoryEnum category) {
        return CompanyCompatibility.builder()
                .userProfile(profile)
                .user(testUser)
                .companyName(companyName)
                .targetRoleCategory(category)
                .targetRoleDetailName("테스트 직무")
                .compatibilityScore(75)
                .summary("테스트 요약")
                .compatibilityMonth(FIXED_COMPATIBILITY_MONTH)
                .build();
    }
}
