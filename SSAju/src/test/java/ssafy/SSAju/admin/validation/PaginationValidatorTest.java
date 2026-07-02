package ssafy.SSAju.admin.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaginationValidator 단위 테스트")
class PaginationValidatorTest {

    private final PaginationValidator validator = new PaginationValidator();

    @Test
    @DisplayName("유효한 page, size 값이면 예외 없이 통과")
    void validate_validValues_doesNotThrow() {
        assertThatCode(() -> validator.validate(0, 20, 50)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("page가 음수이면 IllegalArgumentException 발생")
    void validate_negativePage_throwsException() {
        assertThatThrownBy(() -> validator.validate(-1, 20, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page는 0 이상");
    }

    @Test
    @DisplayName("size가 0이면 IllegalArgumentException 발생")
    void validate_zeroSize_throwsException() {
        assertThatThrownBy(() -> validator.validate(0, 0, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size는 1 이상");
    }

    @Test
    @DisplayName("size가 음수이면 IllegalArgumentException 발생")
    void validate_negativeSize_throwsException() {
        assertThatThrownBy(() -> validator.validate(0, -5, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size는 1 이상");
    }

    @Test
    @DisplayName("size가 maxSize를 초과하면 IllegalArgumentException 발생")
    void validate_sizeExceedsMax_throwsException() {
        assertThatThrownBy(() -> validator.validate(0, 100, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size는 50 이하");
    }
}
