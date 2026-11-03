package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.career.converter.AnalysisSnapshotCodec;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.AnalysisTimeContext;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.mapper.AnalysisSnapshotMapper;
import ssafy.SSAju.dto.response.AnalysisDetailResponse;
import ssafy.SSAju.dto.response.MyPageResponse;
import ssafy.SSAju.dto.response.UserAnalysisDto;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;
import ssafy.SSAju.repository.AnalysisHistoryRepository;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 마이페이지 조회 테스트")
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ANALYSIS_ID = 10L;
    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);

    @Mock private UserRepository userRepository;
    @Mock private AnalysisHistoryRepository analysisHistoryRepository;
    @Mock private SajuRepository sajuRepository;

    private final AnalysisSnapshotCodec snapshotCodec = new AnalysisSnapshotCodec();
    private final AnalysisSnapshotMapper snapshotMapper = new AnalysisSnapshotMapper();
    private final Clock clock = UnifiedAnalysisTestSupport.fixedClock();

    private UserService service;
    private User user;
    private Manse manse;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, analysisHistoryRepository,
                sajuRepository, snapshotCodec, snapshotMapper);
        user = UnifiedAnalysisTestSupport.newUser("reader@test.com");

        UserProfile profile = UnifiedAnalysisTestSupport.newProfile(BIRTH_DATE, BIRTH_TIME);
        manse = Manse.builder()
                .userProfile(profile)
                .data(UnifiedAnalysisTestSupport.manseData())
                .fetchedAt(clock.instant())
                .build();
    }

    @Test
    @DisplayName("유형 필터가 없으면 소유자의 전체 이력을 조회한다")
    void shouldListAllHistoryWithoutTypeFilter() {
        // Given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(analysisHistoryRepository.findAllByUserId(USER_ID, 0, 10)).willReturn(List.of(
                new UserAnalysisDto(AnalysisType.SAJU.name(), 2L, "테스트 사용자", BIRTH_DATE, clock.instant()),
                new UserAnalysisDto(AnalysisType.SAJU.name(), 1L, "테스트 사용자", BIRTH_DATE, clock.instant())));
        given(analysisHistoryRepository.countAllByUserId(USER_ID)).willReturn(2L);

        // When
        MyPageResponse response = service.getMyPage(USER_ID, null, 0, 10);

        // Then
        assertThat(response.analyses()).hasSize(2);
        assertThat(response.pagination().total()).isEqualTo(2);
        assertThat(response.pagination().totalPages()).isEqualTo(1);
        verify(analysisHistoryRepository, never()).findAllByUserIdAndType(anyLong(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("유형 필터가 있으면 목록과 count 모두 같은 조건을 쓴다")
    void shouldUseSameConditionForListAndCount() {
        // Given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(analysisHistoryRepository.findAllByUserIdAndType(
                USER_ID, AnalysisType.CAREER_CONSULTATION.name(), 0, 10)).willReturn(List.of());
        given(analysisHistoryRepository.countAllByUserIdAndType(
                USER_ID, AnalysisType.CAREER_CONSULTATION.name())).willReturn(0L);

        // When
        MyPageResponse response = service.getMyPage(USER_ID, AnalysisType.CAREER_CONSULTATION, 0, 10);

        // Then
        assertThat(response.analyses()).isEmpty();
        assertThat(response.pagination().total()).isZero();
        verify(analysisHistoryRepository, never()).findAllByUserId(anyLong(), anyInt(), anyInt());
        verify(analysisHistoryRepository, never()).countAllByUserId(anyLong());
    }

    @Test
    @DisplayName("관운 상세는 저장된 결과 스냅샷을 그대로 돌려준다")
    void shouldReturnStoredSajuSnapshot() {
        // Given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        givenStoredAnalysis(AnalysisType.SAJU,
                snapshotCodec.writeInput(AnalysisType.SAJU, sajuInput()),
                snapshotCodec.writeResult(AnalysisType.SAJU,
                        AnalysisPayload.SajuResultData.of("H1", 72, "상반기에 관운이 강합니다.")));

        // When
        AnalysisDetailResponse response = service.getAnalysisDetail(USER_ID, ANALYSIS_ID, AnalysisType.SAJU);

        // Then
        assertThat(response.type()).isEqualTo(AnalysisType.SAJU.name());
        assertThat(response.analysisId()).isEqualTo(ANALYSIS_ID);
        assertThat(response.birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(response.careerFortuneDetail().favoredPeriod()).isEqualTo("H1");
        assertThat(response.careerFortuneDetail().confidenceScore()).isEqualTo(72);
        assertThat(response.consultationDetail()).isNull();
        assertThat(response.compatibilityDetail()).isNull();
    }

    @Test
    @DisplayName("없는 ID·타인의 ID·유형 불일치는 모두 같은 미존재 오류다")
    void shouldRejectMissingOtherOwnerAndTypeMismatchAlike() {
        // Given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(sajuRepository.findByIdAndUserIdAndAnalysisType(any(), any(), any()))
                .willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.getAnalysisDetail(USER_ID, 999L, AnalysisType.SAJU))
                .isInstanceOf(SajuResultNotFoundException.class);
        assertThatThrownBy(() -> service.getAnalysisDetail(USER_ID, ANALYSIS_ID, AnalysisType.CAREER_CONSULTATION))
                .isInstanceOf(SajuResultNotFoundException.class);
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 조회하지 않는다")
    void shouldRejectUnknownUser() {
        // Given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.getAnalysisDetail(USER_ID, ANALYSIS_ID, AnalysisType.SAJU))
                .isInstanceOf(UserNotFoundException.class);
        verify(sajuRepository, never()).findByIdAndUserIdAndAnalysisType(any(), any(), any());
    }

    @Test
    @DisplayName("궁합 상세의 대상 이름은 저장된 입력의 회사명을 쓴다")
    void shouldUseStoredCompanyNameForCompatibilityDetail() {
        // Given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        String inputJson = UnifiedAnalysisTestSupport.readFixture(UnifiedAnalysisTestSupport.COMPATIBILITY_INPUT_FIXTURE);
        String resultJson = UnifiedAnalysisTestSupport.readFixture(
                UnifiedAnalysisTestSupport.COMPATIBILITY_RESULT_FIXTURE);
        givenStoredAnalysis(AnalysisType.COMPANY_COMPATIBILITY, inputJson, resultJson);

        // When
        AnalysisDetailResponse response = service.getAnalysisDetail(
                USER_ID, ANALYSIS_ID, AnalysisType.COMPANY_COMPATIBILITY);

        // Then
        assertThat(response.targetName()).isEqualTo("테스트기업");
        assertThat(response.compatibilityDetail().compatibilityId()).isEqualTo(ANALYSIS_ID);
        assertThat(response.compatibilityDetail().compatibilityScore()).isEqualTo(78);
    }

    private void givenStoredAnalysis(AnalysisType type, String inputJson, String resultJson) {
        Saju saju = mock(Saju.class);
        given(saju.getId()).willReturn(ANALYSIS_ID);
        given(saju.getAnalysisType()).willReturn(type);
        given(saju.getCreatedAt()).willReturn(clock.instant());
        given(saju.getInputJson()).willReturn(inputJson);
        given(saju.getResultJson()).willReturn(resultJson);
        given(saju.getManse()).willReturn(manse);
        given(sajuRepository.findByIdAndUserIdAndAnalysisType(ANALYSIS_ID, USER_ID, type))
                .willReturn(Optional.of(saju));
    }

    private AnalysisPayload.SajuInput sajuInput() {
        return AnalysisPayload.SajuInput.of(BIRTH_DATE, BIRTH_TIME, AnalysisTimeContext.from(clock));
    }
}
