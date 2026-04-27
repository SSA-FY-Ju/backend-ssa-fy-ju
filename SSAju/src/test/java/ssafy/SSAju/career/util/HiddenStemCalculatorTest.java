package ssafy.SSAju.career.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HiddenStemCalculatorTest {

    private HiddenStemCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new HiddenStemCalculator();
    }

    @Test
    @DisplayName("子는 지장간 癸만 가진다")
    void shouldReturnOnlyGye_WhenBranchIsJa() {
        // Given & When
        List<String> hidden = calculator.getHiddenStems("子");

        // Then
        assertThat(hidden).containsExactly("癸");
    }

    @Test
    @DisplayName("丑은 지장간 癸, 辛, 己를 가진다")
    void shouldReturnThreeStems_WhenBranchIsChuk() {
        // Given & When
        List<String> hidden = calculator.getHiddenStems("丑");

        // Then
        assertThat(hidden).containsExactly("癸", "辛", "己");
    }

    @Test
    @DisplayName("寅은 지장간 甲, 丙, 戊를 가진다")
    void shouldReturnThreeStems_WhenBranchIsIn() {
        // Given & When
        List<String> hidden = calculator.getHiddenStems("寅");

        // Then
        assertThat(hidden).containsExactly("甲", "丙", "戊");
    }

    @Test
    @DisplayName("4개 지지 목록으로 지장간 맵 계산")
    void shouldCalculateHiddenStemsMap_FromFourBranches() {
        // Given
        List<String> branches = List.of("午", "戌", "未", "子");

        // When
        Map<String, List<String>> result = calculator.calculate(branches);

        // Then
        assertThat(result).containsKey("午");
        assertThat(result).containsKey("戌");
        assertThat(result).containsKey("未");
        assertThat(result).containsKey("子");
        assertThat(result.get("午")).containsExactly("丁", "己");
        assertThat(result.get("子")).containsExactly("癸");
    }

    @Test
    @DisplayName("4개가 아닌 지지 목록은 예외 발생")
    void shouldThrowException_WhenBranchesNotFour() {
        // Given
        List<String> branches = List.of("午", "戌", "未");

        // When & Then
        assertThatThrownBy(() -> calculator.calculate(branches))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("정확히 4개");
    }

    @Test
    @DisplayName("알 수 없는 지지는 예외 발생")
    void shouldThrowException_WhenUnknownBranch() {
        // Given & When & Then
        assertThatThrownBy(() -> calculator.getHiddenStems("甲"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알 수 없는 지지");
    }

    @Test
    @DisplayName("12지지 모두 지장간 매핑이 존재한다")
    void shouldHaveHiddenStemsForAllTwelveBranches() {
        // Given
        List<String> allBranches = List.of("子", "丑", "寅", "卯", "辰", "巳",
                "午", "未", "申", "酉", "戌", "亥");

        // When & Then
        for (String branch : allBranches) {
            assertThat(calculator.getHiddenStems(branch))
                    .as("지지 %s의 지장간이 있어야 합니다", branch)
                    .isNotEmpty();
        }
    }
}
