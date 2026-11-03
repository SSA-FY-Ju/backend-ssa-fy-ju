package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.validator.RequestValidator;
import ssafy.SSAju.dto.request.DefaultProfileRequest;
import ssafy.SSAju.dto.response.DefaultProfileResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.exception.UnauthorizedException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;
import ssafy.SSAju.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultProfileService 단위 테스트")
class DefaultProfileServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);

    @Mock private UserRepository userRepository;
    @Mock private UserProfileProvider userProfileProvider;
    @Mock private DefaultProfileWriteService defaultProfileWriteService;

    private DefaultProfileService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new DefaultProfileService(userRepository, userProfileProvider,
                defaultProfileWriteService, new RequestValidator());
        user = UnifiedAnalysisTestSupport.newUser("default-profile@test.com");
    }

    @Test
    @DisplayName("기본값을 설정하지 않은 사용자는 null을 돌려받는다")
    void shouldReturnNullWhenDefaultProfileIsUnset() {
        // Given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        // When / Then
        assertThat(service.getDefaultProfile(USER_ID)).isNull();
    }

    @Test
    @DisplayName("저장된 기본값의 출생정보를 돌려준다")
    void shouldReturnStoredBirthInfo() {
        // Given
        user.changeDefaultProfile(UnifiedAnalysisTestSupport.newProfile(BIRTH_DATE, BIRTH_TIME));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        // When
        DefaultProfileResponse response = service.getDefaultProfile(USER_ID);

        // Then
        assertThat(response.birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(response.birthTime()).isEqualTo(BIRTH_TIME);
    }

    @Test
    @DisplayName("저장은 공유 프로필을 찾거나 만들고 사용자 참조만 교체한다")
    void shouldReplaceOnlyUserReference() {
        // Given
        UserProfile shared = UnifiedAnalysisTestSupport.newProfile(BIRTH_DATE, BIRTH_TIME);
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(shared);

        // When
        DefaultProfileResponse response = service.changeDefaultProfile(
                USER_ID, new DefaultProfileRequest(BIRTH_DATE, BIRTH_TIME));

        // Then
        assertThat(response.birthDate()).isEqualTo(BIRTH_DATE);
        verify(defaultProfileWriteService).saveReference(USER_ID, shared);
    }

    @Test
    @DisplayName("조회·저장 어디에서도 외부 호출·만세력·분석·차감이 없다")
    void shouldHaveNoSideEffects() {
        // Given
        UserProfile shared = UnifiedAnalysisTestSupport.newProfile(BIRTH_DATE, BIRTH_TIME);
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(shared);

        // When
        service.changeDefaultProfile(USER_ID, new DefaultProfileRequest(BIRTH_DATE, BIRTH_TIME));

        // Then: 협력자는 프로필 조회/생성과 참조 저장뿐이다 — 외부 호출·분석 관련 의존이 아예 없다
        assertThat(DefaultProfileService.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getType)
                .doesNotContain(SajuDataService.class, AnalysisService.class, DailyApiUsageService.class);
    }

    @Test
    @DisplayName("지원 범위를 벗어난 출생정보는 프로필을 만들지 않고 거부한다")
    void shouldRejectUnsupportedBirthInfo() {
        // When / Then
        assertThatThrownBy(() -> service.changeDefaultProfile(
                USER_ID, new DefaultProfileRequest(LocalDate.of(1899, 12, 31), BIRTH_TIME)))
                .isInstanceOf(InvalidSajuDataException.class);
        assertThatThrownBy(() -> service.changeDefaultProfile(
                USER_ID, new DefaultProfileRequest(BIRTH_DATE, null)))
                .isInstanceOf(InvalidSajuDataException.class);
        verifyNoInteractions(userProfileProvider, defaultProfileWriteService);
    }

    @Test
    @DisplayName("인증 정보가 없으면 저장하지 않는다")
    void shouldRejectUnauthenticatedSave() {
        assertThatThrownBy(() -> service.changeDefaultProfile(
                null, new DefaultProfileRequest(BIRTH_DATE, BIRTH_TIME)))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(userProfileProvider, defaultProfileWriteService);
    }

    @Test
    @DisplayName("없는 사용자의 기본값은 조회하지 않는다")
    void shouldRejectUnknownUser() {
        // Given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.getDefaultProfile(USER_ID))
                .isInstanceOf(UserNotFoundException.class);
        verify(userProfileProvider, never()).findOrCreate(any(), any());
    }
}
