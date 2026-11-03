package ssafy.SSAju.career.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AnalysisTimeContext 단위 테스트")
class AnalysisTimeContextTest {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("주입된 Clock의 서비스 기준 날짜로 문맥을 확정한다")
    void shouldFixContextFromClock() {
        // Given: KST 2026-11-02 12:00
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T03:00:00Z"), SERVICE_ZONE);

        // When
        AnalysisTimeContext time = AnalysisTimeContext.from(clock);

        // Then
        assertThat(time.analysisDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(time.year()).isEqualTo(2026);
        assertThat(time.month()).isEqualTo(11);
        assertThat(time.yearMonth()).isEqualTo(202611);
        assertThat(time.targetMonths()).containsExactly(11, 12, 1, 2, 3);
    }

    @Test
    @DisplayName("12월 경계에서 대상 월이 순환한다")
    void shouldCycleTargetMonthsAcrossYearBoundary() {
        // Given: KST 2026-12-31 23:00 (UTC 14:00)
        Clock clock = Clock.fixed(Instant.parse("2026-12-31T14:00:00Z"), SERVICE_ZONE);

        // When
        AnalysisTimeContext time = AnalysisTimeContext.from(clock);

        // Then
        assertThat(time.month()).isEqualTo(12);
        assertThat(time.targetMonths()).containsExactly(12, 1, 2, 3, 4);
    }

    @Test
    @DisplayName("문맥은 한 번 확정되면 이후 시각 변화의 영향을 받지 않는다")
    void shouldStayFixedWhileClockMovesAcrossYear() {
        // Given: 요청 시작 시점의 문맥
        Clock beforeMidnight = Clock.fixed(Instant.parse("2026-12-31T14:50:00Z"), SERVICE_ZONE);
        AnalysisTimeContext time = AnalysisTimeContext.from(beforeMidnight);

        // When: 요청 처리 중 해가 바뀐다
        Clock afterMidnight = Clock.fixed(Instant.parse("2026-12-31T15:10:00Z"), SERVICE_ZONE);
        AnalysisTimeContext laterContext = AnalysisTimeContext.from(afterMidnight);

        // Then: 이미 확정된 문맥은 그대로이고, 새로 만든 문맥만 달라진다
        assertThat(time.year()).isEqualTo(2026);
        assertThat(time.month()).isEqualTo(12);
        assertThat(time.yearMonth()).isEqualTo(202612);
        assertThat(laterContext.year()).isEqualTo(2027);
        assertThat(laterContext.month()).isEqualTo(1);
    }

    @Test
    @DisplayName("대상 월 목록은 수정할 수 없다")
    void shouldKeepTargetMonthsImmutable() {
        // Given
        List<Integer> mutable = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        AnalysisTimeContext time = new AnalysisTimeContext(LocalDate.of(2026, 1, 15), 2026, 1, mutable);

        // When: 원본 목록을 바꿔도
        mutable.set(0, 9);

        // Then: 문맥의 값은 영향받지 않는다
        assertThat(time.targetMonths()).containsExactly(1, 2, 3, 4, 5);
        assertThatThrownBy(() -> time.targetMonths().set(0, 9))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("날짜와 연·월이 어긋나거나 대상 월이 잘못되면 거부한다")
    void shouldRejectInconsistentContext() {
        assertThatThrownBy(() -> new AnalysisTimeContext(null, 2026, 11, List.of(11, 12, 1, 2, 3)))
                .isInstanceOf(InvalidSajuDataException.class);
        assertThatThrownBy(() -> new AnalysisTimeContext(
                LocalDate.of(2026, 11, 2), 2025, 11, List.of(11, 12, 1, 2, 3)))
                .isInstanceOf(InvalidSajuDataException.class);
        assertThatThrownBy(() -> new AnalysisTimeContext(
                LocalDate.of(2026, 11, 2), 2026, 10, List.of(11, 12, 1, 2, 3)))
                .isInstanceOf(InvalidSajuDataException.class);
        assertThatThrownBy(() -> new AnalysisTimeContext(
                LocalDate.of(2026, 11, 2), 2026, 11, List.of(11, 12)))
                .isInstanceOf(InvalidSajuDataException.class);
        assertThatThrownBy(() -> new AnalysisTimeContext(
                LocalDate.of(2026, 11, 2), 2026, 11, List.of(11, 12, 1, 2, 13)))
                .isInstanceOf(InvalidSajuDataException.class);
    }
}
