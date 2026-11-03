package ssafy.SSAju.admin.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.SSAju.admin.dto.UserSearchDTO;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;
import ssafy.SSAju.repository.ManseRepository;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 회원 조회의 분석 건수가 소유한 saju 행 수로 집계되는지 검증한다(T057).
 *
 * <p>분석이 없는 회원도 0으로 포함되고, 탈퇴 회원과 그 분석도 관리자 화면에서 사라지지 않는다.
 */
@Testcontainers
@SpringBootTest
@DisplayName("AdminUserQueryRepository — 회원별 분석 건수 집계 검증")
class AdminUserQueryRepositoryTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);

    @Container
    static MySQLContainer<?> mysql = UnifiedAnalysisTestSupport.newMySqlContainer();

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        UnifiedAnalysisTestSupport.registerMySqlProperties(registry, mysql);
    }

    @Autowired private AdminUserQueryRepository userQueryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private ManseRepository manseRepository;
    @Autowired private SajuRepository sajuRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User analyzedUser;
    private User untouchedUser;
    private Manse manse;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_satisfaction_feedback");
        sajuRepository.deleteAllInBatch();
        manseRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM users");
        userProfileRepository.deleteAllInBatch();

        analyzedUser = userRepository.save(UnifiedAnalysisTestSupport.newUser("admin-user-a@test.com"));
        untouchedUser = userRepository.save(UnifiedAnalysisTestSupport.newUser("admin-user-b@test.com"));

        UserProfile profile = userProfileRepository.save(
                UnifiedAnalysisTestSupport.newProfile(BIRTH_DATE, BIRTH_TIME));
        manse = manseRepository.saveAndFlush(Manse.builder()
                .userProfile(profile)
                .data(UnifiedAnalysisTestSupport.manseData())
                .fetchedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("회원별 분석 건수는 소유한 saju 행 수이며 0건 회원도 포함한다")
    void shouldCountOwnedAnalysesAndIncludeUsersWithoutAny() {
        // Given
        saveAnalysis(analyzedUser.getId(), AnalysisType.SAJU);
        saveAnalysis(analyzedUser.getId(), AnalysisType.CAREER_CONSULTATION);

        // When
        List<UserSearchDTO> users = userQueryRepository.findUsersByKeyset(
                null, null, null, null, null, null, 20);

        // Then
        assertThat(users).hasSize(2);
        assertThat(countOf(users, analyzedUser.getId())).isEqualTo(2);
        assertThat(countOf(users, untouchedUser.getId())).isZero();
    }

    @Test
    @DisplayName("회원 상세의 분석 건수도 같은 기준을 쓴다")
    void shouldUseSameCountForUserDetail() {
        // Given
        saveAnalysis(analyzedUser.getId(), AnalysisType.COMPANY_COMPATIBILITY);

        // When
        Optional<UserSearchDTO> detail = userQueryRepository.findUserById(analyzedUser.getId());
        Optional<UserSearchDTO> emptyDetail = userQueryRepository.findUserById(untouchedUser.getId());

        // Then
        assertThat(detail).isPresent();
        assertThat(detail.get().totalAnalysisCount()).isEqualTo(1);
        assertThat(emptyDetail).isPresent();
        assertThat(emptyDetail.get().totalAnalysisCount()).isZero();
    }

    @Test
    @DisplayName("탈퇴 회원과 그 분석 건수도 관리자 조회에 남는다")
    void shouldIncludeWithdrawnUser() {
        // Given
        saveAnalysis(analyzedUser.getId(), AnalysisType.SAJU);
        jdbcTemplate.update("UPDATE users SET deleted_at = NOW(), status = 'INACTIVE', "
                + "name = '탈퇴한 사용자' WHERE id = ?", analyzedUser.getId());

        // When
        List<UserSearchDTO> users = userQueryRepository.findUsersByKeyset(
                null, null, null, null, null, null, 20);
        Optional<UserSearchDTO> detail = userQueryRepository.findUserById(analyzedUser.getId());

        // Then: soft delete된 계정도 대체된 이름과 함께 집계된다
        assertThat(countOf(users, analyzedUser.getId())).isEqualTo(1);
        assertThat(detail).isPresent();
        assertThat(detail.get().name()).isEqualTo("탈퇴한 사용자");
        assertThat(detail.get().status()).isEqualTo(UserStatus.INACTIVE);
        assertThat(detail.get().totalAnalysisCount()).isEqualTo(1);
    }

    private long countOf(List<UserSearchDTO> users, Long userId) {
        return users.stream()
                .filter(u -> u.id().equals(userId))
                .findFirst()
                .orElseThrow()
                .totalAnalysisCount();
    }

    private void saveAnalysis(Long userId, AnalysisType type) {
        sajuRepository.saveAndFlush(Saju.builder()
                .userId(userId)
                .analysisType(type)
                .manse(manse)
                .inputJson(UnifiedAnalysisTestSupport.readFixture(
                        UnifiedAnalysisTestSupport.COMPATIBILITY_INPUT_FIXTURE))
                .resultJson(UnifiedAnalysisTestSupport.readFixture(
                        UnifiedAnalysisTestSupport.COMPATIBILITY_RESULT_FIXTURE))
                .build());
    }
}
