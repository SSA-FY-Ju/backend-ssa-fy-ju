package ssafy.SSAju.integration;

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
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.domain.TenGodHiddenStemAnalysis;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세 루트 엔티티의 JSON 컬럼(MySQL {@code json} 타입) 저장/조회 라운드트립을 검증한다.
 * H2가 아닌 실제 MySQL을 사용해 {@code columnDefinition = "json"} 컬럼의 실제 저장/조회 동작을 확인한다.
 */
@Testcontainers
@SpringBootTest
@DisplayName("career 도메인 JSON 컬럼 저장/조회 통합 테스트 (MySQL)")
class CareerResultJsonStorageIntegrationTest {

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

    @Autowired private SajuResultRepository sajuResultRepository;
    @Autowired private CareerConsultationRepository careerConsultationRepository;
    @Autowired private CompanyCompatibilityRepository companyCompatibilityRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserRepository userRepository;

    private User testUser;
    private UserProfile testProfile;

    @BeforeEach
    void setUp() {
        careerConsultationRepository.deleteAllInBatch();
        companyCompatibilityRepository.deleteAllInBatch();
        sajuResultRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        testUser = userRepository.save(User.builder()
                .email("json-storage-test@test.com")
                .passwordHash("hash")
                .name("테스트")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(Instant.now())
                .privacyAgreedAt(Instant.now())
                .build());

        testProfile = userProfileRepository.save(UserProfile.builder()
                .birthDate(LocalDate.of(1995, 3, 20))
                .birthTime(LocalTime.of(9, 15))
                .build());
    }

    @Test
    @DisplayName("SajuResult.tenGodHiddenStemAnalysis JSON 컬럼 저장/재조회")
    void shouldPersistAndReload_TenGodHiddenStemAnalysis() {
        SajuResult result = SajuResult.builder()
                .userProfile(testProfile)
                .build();
        result.assignTenGodHiddenStemAnalysis(new TenGodHiddenStemAnalysis(
                Map.of("비견", 2, "겁재", 1),
                Map.of("년주", List.of("갑", "을"))));

        Long id = sajuResultRepository.save(result).getId();
        sajuResultRepository.flush();

        SajuResult reloaded = sajuResultRepository.findById(id).orElseThrow();
        assertThat(reloaded.getTenGodHiddenStemAnalysis().tenGods()).isEqualTo(Map.of("비견", 2, "겁재", 1));
        assertThat(reloaded.getTenGodHiddenStemAnalysis().hiddenStems())
                .isEqualTo(Map.of("년주", List.of("갑", "을")));
    }

    @Test
    @DisplayName("CareerConsultation.resultJson JSON 컬럼 저장/재조회")
    void shouldPersistAndReload_ConsultationResultJson() {
        SajuResult sajuResult = sajuResultRepository.save(
                SajuResult.builder().userProfile(testProfile).build());

        CareerAdviceResponse advice = new CareerAdviceResponse(
                List.of(new CareerAdviceResponse.IndustryRecommendation("IT", "적성 부합", List.of("백엔드"))),
                List.of("면접 팁1"), List.of("강점1"), List.of("유의사항1"),
                null, null, null, null, null, null, null, null, null,
                List.of("정관"), "己土 - 수용적 성향", "金 강세");

        CareerConsultation consultation = CareerConsultation.builder()
                .sajuResult(sajuResult)
                .openaiModelVersion("gpt-4o-mini")
                .consultationMonth(202605)
                .resultJson(advice)
                .build();

        Long id = careerConsultationRepository.save(consultation).getId();
        careerConsultationRepository.flush();

        CareerConsultation reloaded = careerConsultationRepository.findById(id).orElseThrow();
        assertThat(reloaded.getResultJson().industries()).hasSize(1);
        assertThat(reloaded.getResultJson().dayMasterDescription()).isEqualTo("己土 - 수용적 성향");
        assertThat(reloaded.getResultJson().keyTenGods()).containsExactly("정관");
    }

    @Test
    @DisplayName("CompanyCompatibility.resultJson JSON 컬럼 저장/재조회")
    void shouldPersistAndReload_CompatibilityResultJson() {
        CompatibilityAnalysisData data = new CompatibilityAnalysisData(
                new CompatibilityAnalysisData.RoleAnalysis(88, "시너지", "주의"),
                new CompatibilityAnalysisData.FiveElementsInfo(Map.of("木", 2), Map.of("金", 3), "오행 시너지"),
                new CompatibilityAnalysisData.ScoreBreakdown(75, 80, 70),
                new CompatibilityAnalysisData.StrategyInfo(List.of("키워드1"), "약점 보완", List.of("월요일"), "오전"),
                List.of(new CompatibilityAnalysisData.InterviewQuestion("질문1", "의도1")),
                List.of(),
                List.of(),
                List.of("유의사항1")
        );

        CompanyCompatibility compatibility = CompanyCompatibility.builder()
                .userProfile(testProfile)
                .user(testUser)
                .companyName("현대오토에버")
                .targetRoleCategory(JobCategoryEnum.TECH_BACKEND)
                .targetRoleDetailName("백엔드 개발자")
                .compatibilityScore(85)
                .summary("높은 궁합")
                .compatibilityMonth(202605)
                .build();
        compatibility.assignResultJsonAndMarkCompleted(data);

        Long id = companyCompatibilityRepository.save(compatibility).getId();
        companyCompatibilityRepository.flush();

        CompanyCompatibility reloaded = companyCompatibilityRepository.findById(id).orElseThrow();
        assertThat(reloaded.isCompleted()).isTrue();
        assertThat(reloaded.getResultJson().roleAnalysis().matchScore()).isEqualTo(88);
        assertThat(reloaded.getResultJson().fiveElements().userDistribution()).isEqualTo(Map.of("木", 2));
        assertThat(reloaded.getResultJson().cautions()).containsExactly("유의사항1");
    }
}
