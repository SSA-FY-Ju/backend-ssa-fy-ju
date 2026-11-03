package ssafy.SSAju.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.dto.response.MyPageResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.repository.ManseRepository;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.service.SajuDataService;
import ssafy.SSAju.service.UserService;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마이페이지 목록 조회가 행 수와 무관하게 고정된 쿼리 수를 쓰는지 검증한다(T072).
 *
 * <p>세 유형을 한 테이블에서 읽으므로 목록은 "목록 1 + count 1"로 끝나야 하며, 궁합 행의 회사명을
 * 채우려고 행마다 추가 조회를 하지 않아야 한다.
 */
@Testcontainers
@SpringBootTest
@Import(AnalysisReadQueryCountIntegrationTest.StatementCountingConfig.class)
@DisplayName("마이페이지 조회 쿼리 수 검증 (MySQL)")
class AnalysisReadQueryCountIntegrationTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);

    @Container
    static MySQLContainer<?> mysql = UnifiedAnalysisTestSupport.newMySqlContainer();

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        UnifiedAnalysisTestSupport.registerMySqlProperties(registry, mysql);
    }

    @MockitoBean private SajuDataService sajuDataService;

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private ManseRepository manseRepository;
    @Autowired private SajuRepository sajuRepository;

    private User owner;

    @BeforeEach
    void setUp() {
        sajuRepository.deleteAllInBatch();
        manseRepository.deleteAllInBatch();
        userRepository.deleteAll();
        userProfileRepository.deleteAllInBatch();

        owner = userRepository.save(UnifiedAnalysisTestSupport.newUser("query-count@test.com"));
        UserProfile profile = userProfileRepository.save(
                UnifiedAnalysisTestSupport.newProfile(BIRTH_DATE, BIRTH_TIME));
        Manse manse = manseRepository.saveAndFlush(Manse.builder()
                .userProfile(profile)
                .data(UnifiedAnalysisTestSupport.manseData())
                .fetchedAt(Instant.now())
                .build());

        for (int i = 0; i < 12; i++) {
            sajuRepository.saveAndFlush(Saju.builder()
                    .userId(owner.getId())
                    .analysisType(AnalysisType.COMPANY_COMPATIBILITY)
                    .manse(manse)
                    .inputJson(UnifiedAnalysisTestSupport.readFixture(
                            UnifiedAnalysisTestSupport.COMPATIBILITY_INPUT_FIXTURE))
                    .resultJson(UnifiedAnalysisTestSupport.readFixture(
                            UnifiedAnalysisTestSupport.COMPATIBILITY_RESULT_FIXTURE))
                    .build());
        }
    }

    @Test
    @DisplayName("페이지 크기를 늘려도 목록 조회 쿼리 수는 그대로다")
    void shouldKeepQueryCountStableAcrossPageSizes() {
        // When: 2건짜리 페이지
        StatementCountingConfig.STATEMENT_COUNT.set(0);
        MyPageResponse smallPage = userService.getMyPage(owner.getId(), null, 0, 2);
        int smallPageStatements = StatementCountingConfig.STATEMENT_COUNT.get();

        // When: 10건짜리 페이지
        StatementCountingConfig.STATEMENT_COUNT.set(0);
        MyPageResponse largePage = userService.getMyPage(owner.getId(), null, 0, 10);
        int largePageStatements = StatementCountingConfig.STATEMENT_COUNT.get();

        // Then
        assertThat(smallPage.analyses()).hasSize(2);
        assertThat(largePage.analyses()).hasSize(10);
        assertThat(largePageStatements)
                .as("행이 5배가 돼도 쿼리 수는 같아야 한다 (N+1 없음)")
                .isEqualTo(smallPageStatements);
    }

    @Test
    @DisplayName("궁합 행의 회사명을 채우려고 행마다 추가 조회를 하지 않는다")
    void shouldNotQueryPerRowForCompanyName() {
        // When
        StatementCountingConfig.STATEMENT_COUNT.set(0);
        MyPageResponse page = userService.getMyPage(owner.getId(), AnalysisType.COMPANY_COMPATIBILITY, 0, 10);
        int statements = StatementCountingConfig.STATEMENT_COUNT.get();

        // Then: 사용자 조회 + 목록 + count 수준의 고정 비용만 든다
        assertThat(page.analyses()).hasSize(10);
        assertThat(page.analyses()).allSatisfy(dto ->
                assertThat(dto.targetName()).isEqualTo("테스트기업"));
        assertThat(statements)
                .as("행 수(10)보다 훨씬 적은 고정 쿼리 수여야 한다")
                .isLessThanOrEqualTo(5);
    }

    /**
     * 실행된 JDBC 문장 수를 세는 DataSource 래퍼. 조회 수 고정 여부만 보면 되므로 프록시로
     * {@code prepareStatement} 호출만 센다.
     */
    @TestConfiguration
    static class StatementCountingConfig {

        static final AtomicInteger STATEMENT_COUNT = new AtomicInteger();

        @Bean
        static BeanPostProcessor statementCountingDataSourcePostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof DataSource dataSource) {
                        return countingDataSource(dataSource);
                    }
                    return bean;
                }
            };
        }

        private static DataSource countingDataSource(DataSource delegate) {
            InvocationHandler handler = (proxy, method, args) -> {
                Object result = method.invoke(delegate, args);
                if (result instanceof Connection connection) {
                    return countingConnection(connection);
                }
                return result;
            };
            return (DataSource) Proxy.newProxyInstance(
                    DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class}, handler);
        }

        private static Connection countingConnection(Connection delegate) {
            InvocationHandler handler = (proxy, method, args) -> {
                if (method.getName().startsWith("prepare") || "createStatement".equals(method.getName())) {
                    STATEMENT_COUNT.incrementAndGet();
                }
                return method.invoke(delegate, args);
            };
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, handler);
        }
    }
}
