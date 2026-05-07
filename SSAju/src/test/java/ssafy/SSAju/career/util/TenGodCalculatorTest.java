package ssafy.SSAju.career.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.SSAju.career.domain.TenGodDistribution;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenGodCalculatorTest {

    private TenGodCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TenGodCalculator();
    }

    @Test
    @DisplayName("일간 甲(陽木) 기준 십신 계산 - 비견/겁재")
    void shouldCalculateBiGeonAndGeopJae_WhenDayMasterIsGap() {
        // Given
        String dayMaster = "甲"; // 陽木

        // When & Then
        assertThat(calculator.getTenGod(dayMaster, "甲")).isEqualTo("비견");  // 陽木 = 비견
        assertThat(calculator.getTenGod(dayMaster, "乙")).isEqualTo("겁재");  // 陰木 = 겁재
    }

    @Test
    @DisplayName("일간 甲(陽木) 기준 십신 계산 - 식신/상관")
    void shouldCalculateSikSinAndSangGwan_WhenDayMasterIsGap() {
        // Given
        String dayMaster = "甲"; // 陽木 → 火를 생함

        // When & Then
        assertThat(calculator.getTenGod(dayMaster, "丙")).isEqualTo("식신");  // 陽火 = 식신
        assertThat(calculator.getTenGod(dayMaster, "丁")).isEqualTo("상관");  // 陰火 = 상관
    }

    @Test
    @DisplayName("일간 甲(陽木) 기준 십신 계산 - 편재/정재")
    void shouldCalculatePyeonJaeAndJeongJae_WhenDayMasterIsGap() {
        // Given
        String dayMaster = "甲"; // 陽木 → 土를 극함

        // When & Then
        assertThat(calculator.getTenGod(dayMaster, "戊")).isEqualTo("편재");  // 陽土 = 편재
        assertThat(calculator.getTenGod(dayMaster, "己")).isEqualTo("정재");  // 陰土 = 정재
    }

    @Test
    @DisplayName("일간 甲(陽木) 기준 십신 계산 - 편관/정관")
    void shouldCalculatePyeonGwanAndJeongGwan_WhenDayMasterIsGap() {
        // Given
        String dayMaster = "甲"; // 陽木 → 金에 극당함

        // When & Then
        assertThat(calculator.getTenGod(dayMaster, "庚")).isEqualTo("편관");  // 陽金 = 편관(칠살)
        assertThat(calculator.getTenGod(dayMaster, "辛")).isEqualTo("정관");  // 陰金 = 정관
    }

    @Test
    @DisplayName("일간 甲(陽木) 기준 십신 계산 - 편인/정인")
    void shouldCalculatePyeonInAndJeongIn_WhenDayMasterIsGap() {
        // Given
        String dayMaster = "甲"; // 陽木 → 水에 생받음

        // When & Then
        assertThat(calculator.getTenGod(dayMaster, "壬")).isEqualTo("편인");  // 陽水 = 편인
        assertThat(calculator.getTenGod(dayMaster, "癸")).isEqualTo("정인");  // 陰水 = 정인
    }

    @Test
    @DisplayName("4개 천간 목록으로 분포 계산")
    void shouldCalculateDistributionFromFourStems() {
        // Given
        List<String> stems = List.of("庚", "丙", "甲", "庚"); // 年月日時 (일간=甲)

        // When
        TenGodDistribution distribution = calculator.calculate(stems);

        // Then
        assertThat(distribution.getScore("편관")).isEqualTo(2);  // 庚(年), 庚(時)
        assertThat(distribution.getScore("식신")).isEqualTo(1);  // 丙(月)
        assertThat(distribution.asMap()).doesNotContainKey("비견"); // 일간 甲은 제외
    }

    @Test
    @DisplayName("천간 목록이 4개 미만이면 예외 발생")
    void shouldThrowException_WhenStemsLessThanFour() {
        // Given
        List<String> stems = List.of("甲");

        // When & Then
        assertThatThrownBy(() -> calculator.calculate(stems))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("정확히 4개");
    }

    @Test
    @DisplayName("천간 목록이 4개 초과이면 예외 발생")
    void shouldThrowException_WhenStemsMoreThanFour() {
        // Given
        List<String> stems = List.of("甲", "乙", "丙", "丁", "戊");

        // When & Then
        assertThatThrownBy(() -> calculator.calculate(stems))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("정확히 4개");
    }
}
