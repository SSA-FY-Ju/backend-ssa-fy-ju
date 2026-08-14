package ssafy.SSAju.career.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoleCompatibilityCalculator 단위 테스트")
class RoleCompatibilityCalculatorTest {

    private final RoleCompatibilityCalculator calculator = new RoleCompatibilityCalculator();

    @Test
    @DisplayName("calculateSecondary는 primaryScore에서 15를 차감한다")
    void calculateSecondary_appliesPenalty() {
        assertThat(calculator.calculateSecondary(50)).isEqualTo(35);
    }

    @Test
    @DisplayName("calculateSecondary는 결과가 음수면 0으로 하한이 적용된다")
    void calculateSecondary_flooredAtZero() {
        assertThat(calculator.calculateSecondary(10)).isEqualTo(0);
    }
}
