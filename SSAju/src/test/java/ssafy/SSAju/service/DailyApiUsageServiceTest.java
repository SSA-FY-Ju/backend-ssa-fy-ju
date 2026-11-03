package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.exception.DailyLimitExceededException;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;
import ssafy.SSAju.repository.DailyApiUsageRepository;

import java.time.Clock;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyApiUsageService 단위 테스트")
class DailyApiUsageServiceTest {

    private static final Long USER_ID = 1L;
    private static final int INSERTED = 1;
    private static final int UPDATED = 2;
    private static final int NO_OP = 0;

    @Mock private DailyApiUsageRepository dailyApiUsageRepository;

    private final Clock clock = UnifiedAnalysisTestSupport.fixedClock();
    private final LocalDate today = LocalDate.now(UnifiedAnalysisTestSupport.fixedClock());

    private DailyApiUsageService service;

    @BeforeEach
    void setUp() {
        service = new DailyApiUsageService(dailyApiUsageRepository, clock);
    }

    @Test
    @DisplayName("첫 요청과 증가 요청 모두 차감한 날짜를 돌려준다")
    void shouldReturnUsageDateOnSuccess() {
        // Given
        given(dailyApiUsageRepository.upsertUsageIfUnderLimit(
                USER_ID, today, DailyApiUsageService.DAILY_REQUEST_LIMIT))
                .willReturn(INSERTED, UPDATED);

        // When / Then
        assertThat(service.checkAndIncrementDailyUsage(USER_ID)).isEqualTo(today);
        assertThat(service.checkAndIncrementDailyUsage(USER_ID)).isEqualTo(today);
    }

    @Test
    @DisplayName("한도에 도달해 변경이 없으면 429 예외로 거부한다")
    void shouldRejectWhenLimitReached() {
        // Given: 조건부 upsert가 바꾼 행이 없다(= 한도 도달)
        given(dailyApiUsageRepository.upsertUsageIfUnderLimit(
                USER_ID, today, DailyApiUsageService.DAILY_REQUEST_LIMIT))
                .willReturn(NO_OP);

        // When / Then
        assertThatThrownBy(() -> service.checkAndIncrementDailyUsage(USER_ID))
                .isInstanceOf(DailyLimitExceededException.class);
    }

    @Test
    @DisplayName("복원은 전달받은 차감 날짜를 그대로 사용한다")
    void shouldRestoreUsingGivenUsageDate() {
        // Given: 차감과 복원 사이에 자정을 넘겨도 원래 날짜를 복원해야 한다
        LocalDate usageDate = today.minusDays(1);
        given(dailyApiUsageRepository.restoreUsage(USER_ID, usageDate)).willReturn(1);

        // When
        service.restoreDailyUsage(USER_ID, usageDate);

        // Then
        verify(dailyApiUsageRepository).restoreUsage(USER_ID, usageDate);
    }

    @Test
    @DisplayName("복원 실패는 원래 처리 흐름을 막지 않고 원본 예외에 덧붙인다")
    void shouldSuppressRestoreFailureIntoOriginalException() {
        // Given
        RuntimeException restoreFailure = new IllegalStateException("복원 실패");
        willThrow(restoreFailure).given(dailyApiUsageRepository).restoreUsage(USER_ID, today);
        RuntimeException original = new IllegalStateException("외부 호출 실패");

        // When
        service.restoreQuietly(USER_ID, today, original);

        // Then
        assertThat(original.getSuppressed()).containsExactly(restoreFailure);
    }

    @Test
    @DisplayName("복원할 원본 예외가 없어도 복원 실패를 밖으로 던지지 않는다")
    void shouldNotPropagateRestoreFailureWithoutOriginalException() {
        // Given
        willThrow(new IllegalStateException("복원 실패"))
                .given(dailyApiUsageRepository).restoreUsage(USER_ID, today);

        // When / Then: 예외가 전파되지 않는다
        service.restoreQuietly(USER_ID, today);
    }
}
