package ssafy.SSAju.career.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RequestValidator 단위 테스트")
class RequestValidatorTest {

    private RequestValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RequestValidator();
    }

    @Test
    @DisplayName("유효한 생년월일 + 시간 → 예외 없음")
    void validateBirthInfo_valid_noException() {
        assertThatNoException().isThrownBy(() ->
                validator.validateBirthInfo(LocalDate.of(1990, 10, 10), LocalTime.of(14, 30)));
    }

    @Test
    @DisplayName("생년월일 null → InvalidSajuDataException")
    void validateBirthInfo_nullDate_throws() {
        assertThatThrownBy(() -> validator.validateBirthInfo(null, LocalTime.of(14, 30)))
                .isInstanceOf(InvalidSajuDataException.class)
                .hasMessageContaining("생년월일");
    }

    @Test
    @DisplayName("태어난 시간 null → InvalidSajuDataException")
    void validateBirthInfo_nullTime_throws() {
        assertThatThrownBy(() -> validator.validateBirthInfo(LocalDate.of(1990, 10, 10), null))
                .isInstanceOf(InvalidSajuDataException.class)
                .hasMessageContaining("시간");
    }
}
