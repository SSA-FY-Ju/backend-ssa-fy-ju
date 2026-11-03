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
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.dto.request.CareerTimingRequest;
import ssafy.SSAju.dto.request.DefaultProfileRequest;
import ssafy.SSAju.dto.response.AnalysisDetailResponse;
import ssafy.SSAju.dto.response.DefaultProfileResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.repository.DailyApiUsageRepository;
import ssafy.SSAju.repository.ManseRepository;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.service.AnalysisService;
import ssafy.SSAju.service.DefaultProfileService;
import ssafy.SSAju.service.SajuDataService;
import ssafy.SSAju.service.UserService;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 기본 출생정보 저장·변경이 공유 프로필과 기존 분석에 영향을 주지 않는지 검증한다(T046).
 */
@Testcontainers
@SpringBootTest
@DisplayName("기본 출생정보 통합 테스트 (MySQL)")
class DefaultProfileIntegrationTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);
    private static final LocalDate OTHER_BIRTH_DATE = LocalDate.of(1988, 7, 7);
    private static final LocalTime OTHER_BIRTH_TIME = LocalTime.of(6, 30);

    @Container
    static MySQLContainer<?> mysql = UnifiedAnalysisTestSupport.newMySqlContainer();

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        UnifiedAnalysisTestSupport.registerMySqlProperties(registry, mysql);
    }

    @MockitoBean private SajuDataService sajuDataService;

    @Autowired private DefaultProfileService defaultProfileService;
    @Autowired private AnalysisService analysisService;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private ManseRepository manseRepository;
    @Autowired private SajuRepository sajuRepository;
    @Autowired private DailyApiUsageRepository dailyApiUsageRepository;

    private User owner;
    private User otherUser;

    @BeforeEach
    void setUp() {
        dailyApiUsageRepository.deleteAll();
        sajuRepository.deleteAllInBatch();
        manseRepository.deleteAllInBatch();
        userRepository.deleteAll();
        userProfileRepository.deleteAllInBatch();

        owner = userRepository.save(UnifiedAnalysisTestSupport.newUser("default-owner@test.com"));
        otherUser = userRepository.save(UnifiedAnalysisTestSupport.newUser("default-other@test.com"));

        ManseData fixture = UnifiedAnalysisTestSupport.manseData();
        given(sajuDataService.fetchSajuFromFastAPI(any(), any()))
                .willReturn(fixture.toFastApiResponse(BIRTH_DATE.toString(), BIRTH_TIME.toString()));
    }

    @Test
    @DisplayName("가입 직후 기본값은 비어 있고 저장하면 조회된다")
    void shouldStartEmptyAndReturnSavedDefault() {
        // Then: 초기 상태
        assertThat(defaultProfileService.getDefaultProfile(owner.getId())).isNull();

        // When
        defaultProfileService.changeDefaultProfile(
                owner.getId(), new DefaultProfileRequest(BIRTH_DATE, BIRTH_TIME));

        // Then
        DefaultProfileResponse saved = defaultProfileService.getDefaultProfile(owner.getId());
        assertThat(saved.birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(saved.birthTime()).isEqualTo(BIRTH_TIME);
    }

    @Test
    @DisplayName("기본값 저장은 외부 호출·만세력·분석·차감을 만들지 않는다")
    void shouldNotCreateAnalysisArtifacts() {
        // When
        defaultProfileService.changeDefaultProfile(
                owner.getId(), new DefaultProfileRequest(BIRTH_DATE, BIRTH_TIME));

        // Then
        assertThat(userProfileRepository.count()).isEqualTo(1);
        assertThat(manseRepository.count()).isZero();
        assertThat(sajuRepository.count()).isZero();
        assertThat(dailyApiUsageRepository.count()).isZero();
        verifyNoInteractions(sajuDataService);
    }

    @Test
    @DisplayName("같은 출생정보를 고른 두 사용자는 프로필 한 행을 공유한다")
    void shouldShareSingleProfileBetweenUsers() {
        // When
        defaultProfileService.changeDefaultProfile(
                owner.getId(), new DefaultProfileRequest(BIRTH_DATE, BIRTH_TIME));
        defaultProfileService.changeDefaultProfile(
                otherUser.getId(), new DefaultProfileRequest(BIRTH_DATE, BIRTH_TIME));

        // Then: 두 사용자가 같은 출생정보를 골랐으므로 프로필 행은 하나뿐이다
        assertThat(userProfileRepository.count()).isEqualTo(1);
        assertThat(defaultProfileService.getDefaultProfile(owner.getId()))
                .isEqualTo(defaultProfileService.getDefaultProfile(otherUser.getId()));
    }

    @Test
    @DisplayName("기본값을 바꿔도 공유 프로필의 출생정보와 타인의 기본값은 그대로다")
    void shouldNotModifySharedProfileWhenChangingDefault() {
        // Given: 두 사용자가 같은 기본값을 가리킨다
        defaultProfileService.changeDefaultProfile(
                owner.getId(), new DefaultProfileRequest(BIRTH_DATE, BIRTH_TIME));
        defaultProfileService.changeDefaultProfile(
                otherUser.getId(), new DefaultProfileRequest(BIRTH_DATE, BIRTH_TIME));
        UserProfile sharedBefore = userProfileRepository
                .findByBirthDateAndBirthTime(BIRTH_DATE, BIRTH_TIME).orElseThrow();

        // When: 한 사용자가 다른 출생정보로 바꾼다
        defaultProfileService.changeDefaultProfile(
                owner.getId(), new DefaultProfileRequest(OTHER_BIRTH_DATE, OTHER_BIRTH_TIME));

        // Then: 공유 행의 값은 그대로이고 타인의 기본값도 그대로다
        UserProfile shared = userProfileRepository.findById(sharedBefore.getId()).orElseThrow();
        assertThat(shared.getBirthDate()).isEqualTo(BIRTH_DATE);
        assertThat(shared.getBirthTime()).isEqualTo(BIRTH_TIME);
        assertThat(defaultProfileService.getDefaultProfile(otherUser.getId()).birthDate())
                .isEqualTo(BIRTH_DATE);
        assertThat(defaultProfileService.getDefaultProfile(owner.getId()).birthDate())
                .isEqualTo(OTHER_BIRTH_DATE);
        assertThat(userProfileRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("다른 출생정보로 분석해도 기본값은 자동으로 바뀌지 않는다")
    void shouldKeepDefaultWhenAnalyzingWithOtherBirthInfo() {
        // Given
        defaultProfileService.changeDefaultProfile(
                owner.getId(), new DefaultProfileRequest(BIRTH_DATE, BIRTH_TIME));

        // When: 기본값과 다른 출생정보로 분석
        analysisService.analyze(AnalysisType.SAJU,
                new CareerTimingRequest(OTHER_BIRTH_DATE, OTHER_BIRTH_TIME), owner.getId());

        // Then
        DefaultProfileResponse current = defaultProfileService.getDefaultProfile(owner.getId());
        assertThat(current.birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(current.birthTime()).isEqualTo(BIRTH_TIME);
    }

    @Test
    @DisplayName("기본값을 바꿔도 이미 저장된 분석의 출생정보는 그대로다")
    void shouldKeepStoredAnalysisInputWhenDefaultChanges() {
        // Given: 특정 출생정보로 분석을 남긴다
        Long analysisId = analysisService.analyze(AnalysisType.SAJU,
                new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME), owner.getId()).analysisId();
        Long manseIdBefore = manseRepository.findAll().get(0).getId();

        // When: 기본값을 다른 출생정보로 바꾼다
        defaultProfileService.changeDefaultProfile(
                owner.getId(), new DefaultProfileRequest(OTHER_BIRTH_DATE, OTHER_BIRTH_TIME));

        // Then: 분석의 만세력 참조와 출생정보는 변하지 않는다
        AnalysisDetailResponse detail = userService.getAnalysisDetail(
                owner.getId(), analysisId, AnalysisType.SAJU);
        assertThat(detail.birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(sajuRepository.findById(analysisId).orElseThrow().getManse().getId())
                .isEqualTo(manseIdBefore);
        // 기본값 변경은 만세력을 만들지 않으므로 분석이 쓰던 만세력 한 행만 남는다
        assertThat(manseRepository.count()).isEqualTo(1);
    }
}
