package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * CompanyMatchingService 단위 테스트.
 *
 * <p>US5(T035)에서 락+쿼터 로직이 {@link CompanyCompatibilityLockedAnalysisService}로 분리된 뒤,
 * 이 클래스는 사용자/프로필 확인과 락 없는 1차 캐시 조회만 검증한다. 캐시 미스 이후의
 * 사주 계산/AI 호출/저장/쿼터 로직은 {@link CompanyCompatibilityLockedAnalysisServiceTest}에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CompanyMatchingService 단위 테스트")
class CompanyMatchingServiceTest {

    @Mock private UserProfileProvider userProfileProvider;
    @Mock private CompanyCompatibilityRepository companyCompatibilityRepository;
    @Mock private CompatibilityChildReadService childReadService;
    @Mock private UserRepository userRepository;
    @Mock private CompanyCompatibilityLockedAnalysisService lockedAnalysisService;

    private CompanyMatchingService service;

    /** 테스트 고정 날짜: 2026-05-27 KST → compatibilityMonth = 202605 */
    private static final int TEST_COMPATIBILITY_MONTH = 202605;
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-27T01:00:00Z"),  // UTC 01:00 = KST 10:00
            ZoneId.of("Asia/Seoul")
    );

    private static final Long USER_ID = 1L;
    private static final User MOCK_USER = User.builder()
            .email("test@test.com")
            .passwordHash("hash")
            .name("테스트")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .termsAgreedAt(Instant.now())
            .privacyAgreedAt(Instant.now())
            .build();

    private static final LocalDate USER_BIRTH_DATE = LocalDate.of(1998, 5, 7);
    private static final LocalTime USER_BIRTH_TIME = LocalTime.of(14, 30);
    private static final LocalDate COMPANY_FOUNDING_DATE = LocalDate.of(2000, 4, 10);

    private static final UserProfile MOCK_USER_PROFILE = UserProfile.builder()
            .birthDate(USER_BIRTH_DATE)
            .birthTime(USER_BIRTH_TIME)
            .build();

    @BeforeEach
    void setUp() {
        service = new CompanyMatchingService(
                userProfileProvider, companyCompatibilityRepository, childReadService,
                userRepository, FIXED_CLOCK, lockedAnalysisService
        );
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(MOCK_USER));
        given(userProfileProvider.findOrCreate(any(), any())).willReturn(MOCK_USER_PROFILE);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 → UserNotFoundException")
    void shouldThrow_WhenUserNotFound() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());
        CompatibilityRequest request = buildRequest();

        assertThatThrownBy(() -> service.analyzeCompatibility(request, USER_ID))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(lockedAnalysisService);
    }

    @Test
    @DisplayName("이번 달 completed=true 캐시 존재 → 락 없이 즉시 캐시 반환")
    void shouldReturnCachedResult_WhenThisMonthCompleted_withoutAcquiringLock() {
        CompatibilityRequest request = buildRequest();
        CompanyCompatibility completedEntity = buildCompatibility();
        CompatibilityResponse cachedResponse = new CompatibilityResponse(
                completedEntity.getId(), null, 78, "캐시 요약",
                null, null, null, null, null, null, null, null
        );

        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.of(completedEntity));
        given(childReadService.buildFromExisting(completedEntity, request)).willReturn(cachedResponse);

        CompatibilityResponse response = service.analyzeCompatibility(request, USER_ID);

        assertThat(response).isEqualTo(cachedResponse);
        verifyNoInteractions(lockedAnalysisService);
    }

    @Test
    @DisplayName("캐시가 없거나 미완료 → CompanyCompatibilityLockedAnalysisService로 위임")
    void shouldDelegateToLockedAnalysisService_WhenCacheMissOrIncomplete() {
        CompatibilityRequest request = buildRequest();
        CompatibilityResponse delegatedResponse = new CompatibilityResponse(
                99L, null, 78, "위임된 응답",
                null, null, null, null, null, null, null, null
        );

        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.empty());
        given(lockedAnalysisService.analyzeWithLock(
                request, USER_ID, MOCK_USER, MOCK_USER_PROFILE, TEST_COMPATIBILITY_MONTH, USER_BIRTH_TIME))
                .willReturn(delegatedResponse);

        CompatibilityResponse response = service.analyzeCompatibility(request, USER_ID);

        assertThat(response).isEqualTo(delegatedResponse);
        verify(lockedAnalysisService).analyzeWithLock(
                request, USER_ID, MOCK_USER, MOCK_USER_PROFILE, TEST_COMPATIBILITY_MONTH, USER_BIRTH_TIME);
        verify(childReadService, never()).buildFromExisting(any(), any());
    }

    @Test
    @DisplayName("사용자 출생시간 미입력 시 12:00 기본값으로 위임 전에 확정된다")
    void shouldResolveUserBirthTime_beforeDelegating() {
        CompatibilityRequest request = new CompatibilityRequest(
                USER_BIRTH_DATE, null,
                new CompatibilityRequest.TargetRoleRequest(JobCategoryEnum.TECH_BACKEND, "백엔드 개발자"),
                "현대오토에버", COMPANY_FOUNDING_DATE, null
        );
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        nullable(Long.class), nullable(Long.class), anyString(), any(), anyInt()))
                .willReturn(Optional.empty());

        service.analyzeCompatibility(request, USER_ID);

        verify(userProfileProvider).findOrCreate(USER_BIRTH_DATE, LocalTime.of(12, 0));
    }

    private CompatibilityRequest buildRequest() {
        return new CompatibilityRequest(
                USER_BIRTH_DATE, USER_BIRTH_TIME,
                new CompatibilityRequest.TargetRoleRequest(JobCategoryEnum.TECH_BACKEND, "개발자"),
                "현대오토에버", COMPANY_FOUNDING_DATE, LocalTime.of(12, 0)
        );
    }

    private CompanyCompatibility buildCompatibility() {
        CompanyCompatibility entity = CompanyCompatibility.builder()
                .userProfile(MOCK_USER_PROFILE)
                .user(MOCK_USER)
                .companyName("현대오토에버")
                .targetRoleCategory(JobCategoryEnum.TECH_BACKEND)
                .targetRoleDetailName("개발자")
                .compatibilityScore(78)
                .summary("테스트 요약")
                .compatibilityMonth(TEST_COMPATIBILITY_MONTH)
                .build();
        entity.assignResultJsonAndMarkCompleted(null);
        return entity;
    }
}
