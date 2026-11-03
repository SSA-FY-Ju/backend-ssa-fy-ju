package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.domain.AnalysisExecution;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.AnalysisTimeContext;
import ssafy.SSAju.career.domain.BirthInfo;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.provider.ManseProvider;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.strategy.AnalysisOutcome;
import ssafy.SSAju.career.strategy.AnalysisStrategy;
import ssafy.SSAju.dto.request.CareerTimingRequest;
import ssafy.SSAju.dto.request.ConsultationRequest;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.exception.OpenAIApiException;
import ssafy.SSAju.exception.UnauthorizedException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisService 단위 테스트")
class AnalysisServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ANALYSIS_ID = 77L;
    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);

    @Mock private UserProfileProvider userProfileProvider;
    @Mock private ManseProvider manseProvider;
    @Mock private SajuWriteService sajuWriteService;
    @Mock private DailyApiUsageService dailyApiUsageService;

    @SuppressWarnings("unchecked")
    private final AnalysisStrategy<CareerTimingRequest, AnalysisPayload.SajuResultData> sajuStrategy =
            org.mockito.Mockito.mock(AnalysisStrategy.class);
    @SuppressWarnings("unchecked")
    private final AnalysisStrategy<ConsultationRequest, AnalysisPayload.ConsultationResultData> consultationStrategy =
            org.mockito.Mockito.mock(AnalysisStrategy.class);

    private final Clock clock = UnifiedAnalysisTestSupport.fixedClock();
    private final LocalDate today = LocalDate.now(clock);

    private AnalysisService service;
    private Manse manse;

    @BeforeEach
    void setUp() {
        service = new AnalysisService(
                List.of(sajuStrategy, consultationStrategy),
                userProfileProvider, manseProvider, sajuWriteService, dailyApiUsageService, clock);

        UserProfile profile = UnifiedAnalysisTestSupport.newProfile(BIRTH_DATE, BIRTH_TIME);
        manse = Manse.builder()
                .userProfile(profile)
                .data(UnifiedAnalysisTestSupport.manseData())
                .fetchedAt(clock.instant())
                .build();
    }

    @Test
    @DisplayName("유형을 지원하는 전략을 골라 실행하고 저장된 숫자 ID를 돌려준다")
    void shouldSelectSupportingStrategy() {
        // Given
        givenSajuStrategy();
        givenBasisResolved();
        given(sajuWriteService.save(eq(USER_ID), eq(AnalysisType.SAJU), eq(manse), any(), any()))
                .willReturn(ANALYSIS_ID);

        // When
        AnalysisExecution execution = service.analyze(
                AnalysisType.SAJU, new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME), USER_ID);

        // Then
        assertThat(execution.analysisId()).isEqualTo(ANALYSIS_ID);
        assertThat(execution.result()).isInstanceOf(AnalysisPayload.SajuResultData.class);
        verify(sajuStrategy).validateInput(any());
        verify(sajuStrategy).validateResult(any());
        // 첫 번째 전략이 유형을 지원하면 나머지 전략은 건드리지 않는다
        verifyNoInteractions(consultationStrategy);
    }

    @Test
    @DisplayName("지원하는 전략이 없으면 실행하지 않는다")
    void shouldRejectUnsupportedType() {
        // Given: 두 전략 모두 궁합을 지원하지 않는다
        given(sajuStrategy.supports(AnalysisType.COMPANY_COMPATIBILITY)).willReturn(false);
        given(consultationStrategy.supports(AnalysisType.COMPANY_COMPATIBILITY)).willReturn(false);

        // When / Then
        assertThatThrownBy(() -> service.analyze(
                AnalysisType.COMPANY_COMPATIBILITY, new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME), USER_ID))
                .isInstanceOf(InvalidSajuDataException.class);
        verifyNoInteractions(sajuWriteService, dailyApiUsageService);
    }

    @Test
    @DisplayName("인증 정보가 없으면 쿼터를 차감하지 않고 거부한다")
    void shouldRejectUnauthenticatedRequest() {
        // When / Then
        assertThatThrownBy(() -> service.analyze(
                AnalysisType.SAJU, new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME), null))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(dailyApiUsageService, sajuWriteService, manseProvider);
    }

    @Test
    @DisplayName("관운은 일일 쿼터를 차감하지 않는다")
    void shouldNotConsumeQuotaForSajuType() {
        // Given
        givenSajuStrategy();
        givenBasisResolved();
        given(sajuWriteService.save(any(), any(), any(), any(), any())).willReturn(ANALYSIS_ID);

        // When
        service.analyze(AnalysisType.SAJU, new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME), USER_ID);

        // Then
        verifyNoInteractions(dailyApiUsageService);
    }

    @Test
    @DisplayName("컨설팅은 분석 전에 쿼터를 차감하고 성공 시 복원하지 않는다")
    void shouldConsumeQuotaForConsultationType() {
        // Given
        givenConsultationStrategy();
        givenBasisResolved();
        given(consultationStrategy.analyze(any(), any(), any())).willReturn(consultationOutcome());
        given(dailyApiUsageService.checkAndIncrementDailyUsage(USER_ID)).willReturn(today);
        given(sajuWriteService.save(any(), any(), any(), any(), any())).willReturn(ANALYSIS_ID);

        // When
        service.analyze(AnalysisType.CAREER_CONSULTATION,
                new ConsultationRequest(BIRTH_DATE, BIRTH_TIME), USER_ID);

        // Then
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
        verify(dailyApiUsageService, never()).restoreQuietly(any(), any(), any());
        verify(dailyApiUsageService, never()).restoreQuietly(any(), any());
    }

    @Test
    @DisplayName("외부 호출 실패는 차감한 날짜로 쿼터를 복원하고 원본 예외를 전파한다")
    void shouldRestoreQuotaOnExternalFailure() {
        // Given
        givenConsultationStrategy();
        given(dailyApiUsageService.checkAndIncrementDailyUsage(USER_ID)).willReturn(today);
        givenBasisResolvedForConsultation();
        willThrow(new OpenAIApiException("OpenAI 실패"))
                .given(consultationStrategy).analyze(any(), any(ManseData.class), any(AnalysisTimeContext.class));

        // When / Then
        assertThatThrownBy(() -> service.analyze(AnalysisType.CAREER_CONSULTATION,
                new ConsultationRequest(BIRTH_DATE, BIRTH_TIME), USER_ID))
                .isInstanceOf(OpenAIApiException.class);
        verify(dailyApiUsageService).restoreQuietly(eq(USER_ID), eq(today), any(OpenAIApiException.class));
        verify(sajuWriteService, never()).save(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("저장 커밋 전 실패(탈퇴 등)도 원래 날짜로 쿼터를 복원한다")
    void shouldRestoreQuotaWhenSaveFails() {
        // Given
        givenConsultationStrategy();
        givenBasisResolvedForConsultation();
        given(dailyApiUsageService.checkAndIncrementDailyUsage(USER_ID)).willReturn(today);
        given(consultationStrategy.analyze(any(), any(), any()))
                .willReturn(consultationOutcome());
        willThrow(new UserNotFoundException("사용자를 찾을 수 없습니다."))
                .given(sajuWriteService).save(any(), any(), any(), any(), any());

        // When / Then
        assertThatThrownBy(() -> service.analyze(AnalysisType.CAREER_CONSULTATION,
                new ConsultationRequest(BIRTH_DATE, BIRTH_TIME), USER_ID))
                .isInstanceOf(UserNotFoundException.class);
        verify(dailyApiUsageService).restoreQuietly(eq(USER_ID), eq(today), any(UserNotFoundException.class));
    }

    @Test
    @DisplayName("결과 검증 실패는 저장하지 않고 쿼터를 복원한다")
    void shouldRestoreQuotaWhenResultValidationFails() {
        // Given
        givenConsultationStrategy();
        givenBasisResolvedForConsultation();
        given(dailyApiUsageService.checkAndIncrementDailyUsage(USER_ID)).willReturn(today);
        given(consultationStrategy.analyze(any(), any(), any())).willReturn(consultationOutcome());
        willThrow(new InvalidSajuDataException("분석 결과가 유효하지 않습니다."))
                .given(consultationStrategy).validateResult(any());

        // When / Then
        assertThatThrownBy(() -> service.analyze(AnalysisType.CAREER_CONSULTATION,
                new ConsultationRequest(BIRTH_DATE, BIRTH_TIME), USER_ID))
                .isInstanceOf(InvalidSajuDataException.class);
        verify(sajuWriteService, never()).save(any(), any(), any(), any(), any());
        verify(dailyApiUsageService).restoreQuietly(eq(USER_ID), eq(today), any(InvalidSajuDataException.class));
    }

    @Test
    @DisplayName("같은 입력을 반복해도 매번 새로 분석하고 저장한다")
    void shouldAnalyzeAndSaveForEveryRequest() {
        // Given
        givenSajuStrategy();
        givenBasisResolved();
        given(sajuWriteService.save(any(), any(), any(), any(), any())).willReturn(ANALYSIS_ID, ANALYSIS_ID + 1);
        CareerTimingRequest request = new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME);

        // When
        AnalysisExecution first = service.analyze(AnalysisType.SAJU, request, USER_ID);
        AnalysisExecution second = service.analyze(AnalysisType.SAJU, request, USER_ID);

        // Then
        assertThat(first.analysisId()).isNotEqualTo(second.analysisId());
        verify(sajuStrategy, times(2)).analyze(any(), any(), any());
        verify(sajuWriteService, times(2)).save(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("외부 호출을 포함한 실행 경로에는 트랜잭션을 걸지 않는다")
    void shouldNotBeTransactional() {
        assertThat(AnalysisService.class.isAnnotationPresent(Transactional.class)).isFalse();
        for (Method method : AnalysisService.class.getDeclaredMethods()) {
            assertThat(method.isAnnotationPresent(Transactional.class))
                    .as("AnalysisService.%s", method.getName())
                    .isFalse();
        }
    }

    private void givenSajuStrategy() {
        given(sajuStrategy.supports(AnalysisType.SAJU)).willReturn(true);
        given(sajuStrategy.inputType()).willReturn(CareerTimingRequest.class);
        given(sajuStrategy.birthInfo(any())).willReturn(new BirthInfo(BIRTH_DATE, BIRTH_TIME));
        given(sajuStrategy.analyze(any(), any(), any())).willReturn(new AnalysisOutcome<>(
                AnalysisPayload.SajuInput.of(BIRTH_DATE, BIRTH_TIME, AnalysisTimeContext.from(clock)),
                AnalysisPayload.SajuResultData.of("H1", 75, "근거")));
    }

    private void givenConsultationStrategy() {
        given(consultationStrategy.supports(AnalysisType.CAREER_CONSULTATION)).willReturn(true);
        given(consultationStrategy.inputType()).willReturn(ConsultationRequest.class);
        given(consultationStrategy.birthInfo(any())).willReturn(new BirthInfo(BIRTH_DATE, BIRTH_TIME));
    }

    private void givenBasisResolved() {
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(manse.getUserProfile());
        given(manseProvider.findOrCreate(manse.getUserProfile())).willReturn(manse);
    }

    private void givenBasisResolvedForConsultation() {
        givenBasisResolved();
    }

    private AnalysisOutcome<AnalysisPayload.ConsultationResultData> consultationOutcome() {
        return new AnalysisOutcome<>(
                AnalysisPayload.ConsultationInput.of(BIRTH_DATE, BIRTH_TIME, AnalysisTimeContext.from(clock)),
                new AnalysisPayload.ConsultationResultData(
                        AnalysisPayload.SCHEMA_VERSION, List.of(), List.of(), List.of(), "gpt-4o-mini",
                        "H1", 70, "근거", null, List.of(), null, null, null, null, null, null, null, null,
                        null, "요약"));
    }
}
