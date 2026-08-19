package ssafy.SSAju.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.career.enums.FeedbackType;
import ssafy.SSAju.career.enums.SatisfactionStatus;
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
import ssafy.SSAju.repository.UserSatisfactionFeedbackRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US3(T023): JSON 마이그레이션으로 삭제된 정규화 자식/손자 테이블과 Repository 빈이
 * 더 이상 존재하지 않음을 확인한다.
 *
 * <p>실제 프로덕션/개발 DB에 대한 수동 DROP/TRUNCATE(quickstart.md §2, T022)는 이 테스트의
 * 범위가 아니다 — 이 테스트는 Testcontainers가 현재 엔티티 정의로부터 새로 만든 스키마를
 * 대상으로, 삭제된 엔티티들이 더 이상 테이블/빈으로 되살아나지 않는지를 코드 레벨에서 검증한다.
 */
@Testcontainers
@SpringBootTest
@DisplayName("JSON 마이그레이션 레거시 정리 검증 (US3)")
class CareerResultLegacyDataCleanupIntegrationTest {

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

    private static final Set<String> DROPPED_TABLES = Set.of(
            "industry", "industry_recommended_role", "interview_tip", "strength",
            "consultation_caution", "consultation_key_ten_god", "consultation_wealth_style",
            "consultation_roadmap", "consultation_personal_branding", "consultation_power_keywords",
            "consultation_power_keyword", "consultation_power_keyword_usage_tip",
            "consultation_mental_care", "consultation_mental_stress_factor",
            "consultation_mental_recharge_method", "consultation_environment_fit",
            "consultation_work_style", "consultation_relationship_strategy",
            "consultation_career_timeline", "consultation_month_fortune",
            "consultation_pivot_point", "consultation_warning_month",
            "ten_god_data", "hidden_stem_data",
            "target_role_analysis", "five_elements_analysis", "analysis_breakdown",
            "actionable_strategy", "actionable_keyword", "lucky_day",
            "expected_interview_question", "role_compatibility", "monthly_forecast", "caution"
    );

    private static final Set<String> DELETED_REPOSITORY_BEANS = Set.of(
            "tenGodDataRepository", "hiddenStemDataRepository",
            "industryRepository", "interviewTipRepository", "strengthRepository",
            "targetRoleAnalysisRepository", "fiveElementsAnalysisRepository", "analysisBreakdownRepository",
            "actionableStrategyRepository", "expectedInterviewQuestionRepository",
            "roleCompatibilityRepository", "monthlyForecastRepository", "cautionRepository",
            "actionableKeywordRepository", "luckyDayRepository"
    );

    @Autowired private ApplicationContext applicationContext;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private SajuResultRepository sajuResultRepository;
    @Autowired private CareerConsultationRepository careerConsultationRepository;
    @Autowired private CompanyCompatibilityRepository companyCompatibilityRepository;
    @Autowired private UserSatisfactionFeedbackRepository feedbackRepository;

    private User testUser;
    private UserProfile testProfile;

    private static CareerAdviceResponse minimalAdvice() {
        return new CareerAdviceResponse(
                List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null, null, null,
                List.of("정관"), "일간 설명", "오행 분석");
    }

    @BeforeEach
    void setUp() {
        // 테스트 격리: 같은 컨테이너를 재사용하는 다른 테스트 메서드가 남긴 데이터와
        // 충돌하지 않도록 FK 자식 → 부모 순서로 초기화 (CareerApiIntegrationTest와 동일 패턴)
        feedbackRepository.deleteAll();
        careerConsultationRepository.deleteAll();
        companyCompatibilityRepository.deleteAll();
        sajuResultRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("legacy-cleanup-test@test.com")
                .passwordHash("hash")
                .name("테스트")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(Instant.now())
                .privacyAgreedAt(Instant.now())
                .build());
        testProfile = userProfileRepository.save(UserProfile.builder()
                .birthDate(java.time.LocalDate.of(1997, 8, 12))
                .birthTime(java.time.LocalTime.of(10, 0))
                .build());
    }

    @Test
    @DisplayName("(a) 삭제된 자식/손자 테이블은 SHOW TABLES에 나타나지 않는다")
    void droppedChildTables_areNotPresentInSchema() {
        List<String> actualTables = jdbcTemplate.queryForList("SHOW TABLES", String.class);
        Set<String> actualTablesLower = actualTables.stream().map(String::toLowerCase).collect(java.util.stream.Collectors.toSet());

        for (String dropped : DROPPED_TABLES) {
            assertThat(actualTablesLower).as("삭제 대상 테이블 %s는 더 이상 존재하면 안 됨", dropped)
                    .doesNotContain(dropped);
        }
    }

    @Test
    @DisplayName("(d) 삭제된 자식 엔티티의 Repository 빈은 더 이상 컨텍스트에 존재하지 않는다")
    void deletedChildRepositoryBeans_areNotInApplicationContext() {
        for (String beanName : DELETED_REPOSITORY_BEANS) {
            assertThat(applicationContext.containsBean(beanName))
                    .as("삭제된 Repository 빈 %s는 더 이상 존재하면 안 됨", beanName)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("(c) UserSatisfactionFeedback은 FK만 NULL로 해제되고 행 자체는 보존 가능해야 한다")
    void feedbackForeignKeys_canBeNulledWithoutDeletingRow() {
        CareerConsultation consultation = careerConsultationRepository.save(
                CareerConsultation.builder()
                        .sajuResult(sajuResultRepository.save(
                                SajuResult.builder().userProfile(testProfile).user(testUser).build()))
                        .openaiModelVersion("gpt-4o-mini")
                        .consultationMonth(202605)
                        .resultJson(minimalAdvice())
                        .build());

        UserSatisfactionFeedback feedback = feedbackRepository.save(UserSatisfactionFeedback.builder()
                .user(testUser)
                .careerConsultation(consultation)
                .feedbackType(FeedbackType.CONSULTATION)
                .satisfactionStatus(SatisfactionStatus.SATISFIED)
                .feedbackContent("도움이 됐어요")
                .build());
        Long feedbackId = feedback.getId();

        // FK만 NULL로 해제 — 행 자체(내용)는 보존 (quickstart.md §2-1과 동일한 절차)
        jdbcTemplate.update("UPDATE user_satisfaction_feedback SET career_consultation_id = NULL WHERE id = ?",
                feedbackId);

        UserSatisfactionFeedback reloaded = feedbackRepository.findById(feedbackId).orElseThrow();
        assertThat(reloaded.getCareerConsultation()).isNull();
        assertThat(reloaded.getFeedbackContent()).isEqualTo("도움이 됐어요");
    }

    @Test
    @DisplayName("(b 유사) 새 스키마로 저장한 CompanyCompatibility는 자식 테이블 없이 resultJson만으로 재조회 시 완결된 데이터를 복원한다")
    void newSchemaWrites_doNotDependOnDroppedChildTables() {
        CompatibilityAnalysisData analysisData = new CompatibilityAnalysisData(
                new CompatibilityAnalysisData.RoleAnalysis(88, "시너지", "주의"),
                new CompatibilityAnalysisData.FiveElementsInfo(Map.of("木", 2), Map.of("金", 3), "오행 시너지"),
                new CompatibilityAnalysisData.ScoreBreakdown(75, 80, 70),
                new CompatibilityAnalysisData.StrategyInfo(List.of("키워드1"), "약점 보완", List.of("월요일"), "오전"),
                List.of(new CompatibilityAnalysisData.InterviewQuestion("질문1", "의도1")),
                List.of(),
                List.of(),
                List.of("유의사항1")
        );

        CompanyCompatibility built = CompanyCompatibility.builder()
                .userProfile(testProfile)
                .user(testUser)
                .companyName("현대오토에버")
                .targetRoleCategory(JobCategoryEnum.TECH_BACKEND)
                .targetRoleDetailName("백엔드 개발자")
                .compatibilityScore(80)
                .summary("요약")
                .compatibilityMonth(202605)
                .build();
        built.assignResultJsonAndMarkCompleted(analysisData);

        // save()/findById()는 각각 SimpleJpaRepository의 독립된 트랜잭션(영속성 컨텍스트)에서 실행되므로
        // 별도 flush/clear 없이도 findById가 1차 캐시가 아닌 DB에서 새로 조회함
        Long id = companyCompatibilityRepository.save(built).getId();

        CompanyCompatibility reloaded = companyCompatibilityRepository.findById(id).orElseThrow();
        assertThat(reloaded.isCompleted()).isTrue();
        assertThat(reloaded.getResultJson().roleAnalysis().matchScore()).isEqualTo(88);
        assertThat(reloaded.getResultJson().fiveElements().userDistribution()).isEqualTo(Map.of("木", 2));
        assertThat(reloaded.getResultJson().strategy().keywords()).containsExactly("키워드1");
        assertThat(reloaded.getResultJson().cautions()).containsExactly("유의사항1");
        assertThat(companyCompatibilityRepository.count()).isEqualTo(1);
    }
}
