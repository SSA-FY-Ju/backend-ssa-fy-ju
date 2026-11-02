package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.career.converter.AnalysisSnapshotCodec;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.AnalysisTimeContext;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SajuWriteService 단위 테스트")
class SajuWriteServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);

    @Mock private SajuRepository sajuRepository;
    @Mock private UserRepository userRepository;

    private final AnalysisSnapshotCodec snapshotCodec = new AnalysisSnapshotCodec();
    private final Clock clock = UnifiedAnalysisTestSupport.fixedClock();

    private SajuWriteService service;
    private Manse manse;

    @BeforeEach
    void setUp() {
        service = new SajuWriteService(sajuRepository, userRepository, snapshotCodec);
        manse = Manse.builder()
                .userProfile(UnifiedAnalysisTestSupport.newProfile(BIRTH_DATE, BIRTH_TIME))
                .data(UnifiedAnalysisTestSupport.manseData())
                .fetchedAt(clock.instant())
                .build();
    }

    @Test
    @DisplayName("활성 사용자의 완성 스냅샷은 새 숫자 ID로 저장된다")
    void shouldPersistSnapshotForActiveUser() {
        // Given
        givenActiveUser();
        Saju saved = mock(Saju.class);
        given(saved.getId()).willReturn(7L);
        given(sajuRepository.save(any(Saju.class))).willReturn(saved);

        // When
        Long analysisId = service.save(USER_ID, AnalysisType.SAJU, manse, input(), result());

        // Then
        assertThat(analysisId).isEqualTo(7L);
        ArgumentCaptor<Saju> captor = ArgumentCaptor.forClass(Saju.class);
        verify(sajuRepository).save(captor.capture());
        Saju persisted = captor.getValue();
        assertThat(persisted.getUserId()).isEqualTo(USER_ID);
        assertThat(persisted.getAnalysisType()).isEqualTo(AnalysisType.SAJU);
        assertThat(persisted.getManse()).isSameAs(manse);
        assertThat(persisted.getInputJson()).contains("\"birthDate\"");
        assertThat(persisted.getResultJson()).contains("\"favoredPeriod\"");
    }

    @Test
    @DisplayName("탈퇴로 조회되지 않는 사용자의 최종 저장은 거부된다")
    void shouldRejectSaveForWithdrawnUser() {
        // Given
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.save(USER_ID, AnalysisType.SAJU, manse, input(), result()))
                .isInstanceOf(UserNotFoundException.class);
        verify(sajuRepository, never()).save(any());
    }

    @Test
    @DisplayName("비활성 상태 사용자의 최종 저장은 거부된다")
    void shouldRejectSaveForInactiveUser() {
        // Given
        User inactive = User.builder()
                .email("inactive@test.com")
                .passwordHash("hash")
                .name("비활성")
                .role(UserRole.USER)
                .status(UserStatus.INACTIVE)
                .termsAgreedAt(clock.instant())
                .privacyAgreedAt(clock.instant())
                .build();
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(inactive));

        // When / Then
        assertThatThrownBy(() -> service.save(USER_ID, AnalysisType.SAJU, manse, input(), result()))
                .isInstanceOf(UserNotFoundException.class);
        verify(sajuRepository, never()).save(any());
    }

    @Test
    @DisplayName("유형과 맞지 않는 스냅샷은 사용자 잠금 전에 거부된다")
    void shouldRejectSnapshotTypeMismatchBeforeLocking() {
        // When / Then: 관운 유형에 컨설팅 입력 스냅샷
        assertThatThrownBy(() -> service.save(USER_ID, AnalysisType.CAREER_CONSULTATION, manse, input(), result()))
                .isInstanceOf(InvalidSajuDataException.class);
        verify(userRepository, never()).findByIdForUpdate(any());
        verify(sajuRepository, never()).save(any());
    }

    private void givenActiveUser() {
        given(userRepository.findByIdForUpdate(USER_ID))
                .willReturn(Optional.of(UnifiedAnalysisTestSupport.newUser("writer@test.com")));
    }

    private AnalysisPayload.Input input() {
        return AnalysisPayload.SajuInput.of(BIRTH_DATE, BIRTH_TIME, AnalysisTimeContext.from(clock));
    }

    private AnalysisPayload.Result result() {
        return AnalysisPayload.SajuResultData.of("H1", 72, "상반기에 관운이 강합니다.");
    }
}
