package ssafy.SSAju.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.config.ClockConfig;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.ExternalApiException;
import ssafy.SSAju.repository.DailyApiUsageRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.service.CompanyMatchingService;
import ssafy.SSAju.service.SajuDataService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 기업 궁합 분석 외부 호출(FastAPI) 실패 시 일일 쿼터가 소진된 채 남지 않는지 검증 (US2, T022).
 * MySQL ON DUPLICATE KEY UPDATE 문법을 사용하므로 Testcontainers 필수.
 */
@Testcontainers
@SpringBootTest
@DisplayName("일일 쿼터 무결성 통합 테스트 (US2)")
class DailyQuotaIntegrityIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.url", () -> mysql.getJdbcUrl() + "?useAffectedRows=true");
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired
    private CompanyMatchingService companyMatchingService;

    @Autowired
    private DailyApiUsageRepository dailyApiUsageRepository;

    @Autowired
    private UserRepository userRepository;

    // 외부 API 경계만 mock (FastAPI)
    @MockitoBean
    private SajuDataService sajuDataService;

    private User testUser;
    private CompatibilityRequest request;

    @BeforeEach
    void setUp() {
        dailyApiUsageRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("quota-test@test.com")
                .passwordHash("hash")
                .name("쿼터테스터")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(Instant.now())
                .privacyAgreedAt(Instant.now())
                .build());

        request = new CompatibilityRequest(
                LocalDate.of(1990, 10, 10), null,
                new CompatibilityRequest.TargetRoleRequest(JobCategoryEnum.TECH_BACKEND, null),
                "테스트기업",
                LocalDate.of(2000, 1, 1), null
        );
    }

    private FastAPIResponse fakeFastApiResponse() {
        return new FastAPIResponse(
                List.of("庚", "丙", "己", "辛"),
                List.of("午", "戌", "未", "寅"),
                Map.of("木", 1, "火", 2, "土", 1, "金", 2, "水", 2),
                "庚午", "丙戌", "己未", "辛寅",
                "14:30", "1990-10-10", null
        );
    }

    @Test
    @DisplayName("FastAPI 호출 실패 시 쿼터가 요청 전과 동일하게 유지된다")
    void quotaRestoredWhenFastApiCallFails() {
        given(sajuDataService.fetchSajuFromFastAPI(any(), any()))
                .willThrow(new ExternalApiException("FastAPI 서버 오류"));

        assertThatThrownBy(() -> companyMatchingService.analyzeCompatibility(request, testUser.getId()))
                .isInstanceOf(ExternalApiException.class);

        assertThat(dailyApiUsageRepository.findByUserIdAndUsageDate(testUser.getId(), LocalDate.now(ClockConfig.SERVICE_ZONE))
                .map(usage -> usage.getRequestCount())
                .orElse(0))
                .isZero();
    }

    @Test
    @DisplayName("분석 성공 시 쿼터가 정확히 1 차감된다")
    void quotaDecrementedByOneWhenAnalysisSucceeds() {
        given(sajuDataService.fetchSajuFromFastAPI(any(), any()))
                .willReturn(fakeFastApiResponse());

        companyMatchingService.analyzeCompatibility(request, testUser.getId());

        assertThat(dailyApiUsageRepository.findByUserIdAndUsageDate(testUser.getId(), LocalDate.now(ClockConfig.SERVICE_ZONE))
                .map(usage -> usage.getRequestCount())
                .orElse(0))
                .isEqualTo(1);
    }
}
