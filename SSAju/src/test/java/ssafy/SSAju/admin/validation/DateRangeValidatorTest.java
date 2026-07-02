package ssafy.SSAju.admin.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.SSAju.exception.InvalidDateRangeException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DateRangeValidator 단위 테스트")
class DateRangeValidatorTest {

    private final DateRangeValidator validator = new DateRangeValidator();

    @Test
    @DisplayName("from이 to보다 이전이면 예외 없이 통과")
    void validate_fromBeforeTo_doesNotThrow() {
        // Given
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        // When & Then
        assertThatCode(() -> validator.validate(from, to)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("from과 to가 같으면 예외 없이 통과")
    void validate_fromEqualsTo_doesNotThrow() {
        // Given
        LocalDate date = LocalDate.of(2026, 1, 1);

        // When & Then
        assertThatCode(() -> validator.validate(date, date)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("from이 to보다 늦으면 InvalidDateRangeException 발생")
    void validate_fromAfterTo_throwsException() {
        // Given
        LocalDate from = LocalDate.of(2026, 2, 1);
        LocalDate to = LocalDate.of(2026, 1, 1);

        // When & Then
        assertThatThrownBy(() -> validator.validate(from, to))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("시작일이 종료일보다 늦을 수 없습니다");
    }

    @Test
    @DisplayName("from 또는 to가 null이면 검증을 건너뜀")
    void validate_nullValues_doesNotThrow() {
        assertThatCode(() -> validator.validate(null, LocalDate.now())).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(LocalDate.now(), null)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(null, null)).doesNotThrowAnyException();
    }
}
