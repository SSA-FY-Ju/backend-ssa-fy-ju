package ssafy.SSAju.admin.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FilterValidator 단위 테스트")
class FilterValidatorTest {

    private final FilterValidator validator = new FilterValidator();

    @Test
    @DisplayName("허용된 analysisType이면 예외 없이 통과")
    void validateAnalysisType_validValue_doesNotThrow() {
        assertThatCode(() -> validator.validateAnalysisType("SAJU")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateAnalysisType("CAREER_CONSULTATION")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateAnalysisType("COMPANY_COMPATIBILITY")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("analysisType이 null이면 검증을 건너뜀")
    void validateAnalysisType_null_doesNotThrow() {
        assertThatCode(() -> validator.validateAnalysisType(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("허용되지 않는 analysisType이면 IllegalArgumentException 발생")
    void validateAnalysisType_invalidValue_throwsException() {
        assertThatThrownBy(() -> validator.validateAnalysisType("UNKNOWN_TYPE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않는 analysisType");
    }

    @Test
    @DisplayName("허용된 status이면 예외 없이 통과")
    void validateUserStatus_validValue_doesNotThrow() {
        assertThatCode(() -> validator.validateUserStatus("ACTIVE")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateUserStatus("INACTIVE")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("status가 null이면 검증을 건너뜀")
    void validateUserStatus_null_doesNotThrow() {
        assertThatCode(() -> validator.validateUserStatus(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("허용되지 않는 status이면 IllegalArgumentException 발생")
    void validateUserStatus_invalidValue_throwsException() {
        assertThatThrownBy(() -> validator.validateUserStatus("BANNED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않는 status");
    }
}
