package ssafy.SSAju.career.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SajuValidator 단위 테스트")
class SajuValidatorTest {

    private SajuValidator validator;

    private static final FastAPIResponse VALID_RESPONSE = new FastAPIResponse(
            List.of("庚", "甲", "己", "丁"),
            List.of("午", "戌", "未", "寅"),
            Map.of("木", 1, "火", 2, "土", 2, "金", 2, "水", 1),
            "庚午", "甲戌", "己未", "丁寅",
            "14:30", "1990-10-10", Map.of()
    );

    @BeforeEach
    void setUp() {
        validator = new SajuValidator();
    }

    @Test
    @DisplayName("유효한 응답 → 예외 없음")
    void validate_validResponse_noException() {
        assertThatNoException().isThrownBy(() -> validator.validate(VALID_RESPONSE));
    }

    @Test
    @DisplayName("null 응답 → InvalidSajuDataException")
    void validate_nullResponse_throws() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidSajuDataException.class);
    }

    @Test
    @DisplayName("천간 4개 미만 → InvalidSajuDataException")
    void validate_insufficientHeavenlyStems_throws() {
        var bad = new FastAPIResponse(
                List.of("庚", "甲", "己"),
                List.of("午", "戌", "未", "寅"),
                null, null, null, null, null, null, null, null
        );
        assertThatThrownBy(() -> validator.validate(bad))
                .isInstanceOf(InvalidSajuDataException.class)
                .hasMessageContaining("천간");
    }

    @Test
    @DisplayName("지지 4개 미만 → InvalidSajuDataException")
    void validate_insufficientEarthlyBranches_throws() {
        var bad = new FastAPIResponse(
                List.of("庚", "甲", "己", "丁"),
                List.of("午", "戌", "未"),
                null, null, null, null, null, null, null, null
        );
        assertThatThrownBy(() -> validator.validate(bad))
                .isInstanceOf(InvalidSajuDataException.class)
                .hasMessageContaining("지지");
    }

    @Test
    @DisplayName("오행 없는 응답 → validateWithFiveElements에서 예외")
    void validateWithFiveElements_missingFiveElements_throws() {
        var noFiveElements = new FastAPIResponse(
                List.of("庚", "甲", "己", "丁"),
                List.of("午", "戌", "未", "寅"),
                null, null, null, null, null, null, null, null
        );
        assertThatThrownBy(() -> validator.validateWithFiveElements(noFiveElements))
                .isInstanceOf(InvalidSajuDataException.class)
                .hasMessageContaining("오행");
    }

    @Test
    @DisplayName("오행 포함 유효한 응답 → validateWithFiveElements 예외 없음")
    void validateWithFiveElements_validResponse_noException() {
        assertThatNoException().isThrownBy(() -> validator.validateWithFiveElements(VALID_RESPONSE));
    }
}
