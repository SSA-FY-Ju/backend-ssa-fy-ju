package ssafy.SSAju.career.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.SSAju.career.domain.HiddenStems;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CompatibilityValidator 단위 테스트")
class CompatibilityValidatorTest {

    private CompatibilityValidator validator;

    private static final HiddenStems USER_STEMS =
            new HiddenStems(Map.of("子", List.of("癸")));
    private static final HiddenStems COMPANY_STEMS =
            new HiddenStems(Map.of("午", List.of("丁", "己")));

    @BeforeEach
    void setUp() {
        validator = new CompatibilityValidator();
    }

    @Test
    @DisplayName("모든 인자 유효 → 예외 없음")
    void validate_allValid_noException() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(USER_STEMS, "甲", COMPANY_STEMS, "庚"));
    }

    @Test
    @DisplayName("사용자 지장간 null → IllegalArgumentException")
    void validate_nullUserHiddenStems_throws() {
        assertThatThrownBy(() -> validator.validate(null, "甲", COMPANY_STEMS, "庚"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자 지장간");
    }

    @Test
    @DisplayName("사용자 일간 null → IllegalArgumentException")
    void validate_nullUserDayMaster_throws() {
        assertThatThrownBy(() -> validator.validate(USER_STEMS, null, COMPANY_STEMS, "庚"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자 일간");
    }

    @Test
    @DisplayName("기업 지장간 null → IllegalArgumentException")
    void validate_nullCompanyHiddenStems_throws() {
        assertThatThrownBy(() -> validator.validate(USER_STEMS, "甲", null, "庚"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("기업 지장간");
    }

    @Test
    @DisplayName("기업 일간 빈 문자열 → IllegalArgumentException")
    void validate_blankCompanyDayMaster_throws() {
        assertThatThrownBy(() -> validator.validate(USER_STEMS, "甲", COMPANY_STEMS, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("기업 일간");
    }
}
