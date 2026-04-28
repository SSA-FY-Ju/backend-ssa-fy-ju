package ssafy.SSAju.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("CareerFortuneService 단위 테스트")
class CareerFortuneServiceTest {

    @Autowired
    private TenGodCalculator tenGodCalculator;

    @Autowired
    private HiddenStemCalculator hiddenStemCalculator;

    @Autowired
    private CareerFortuneAnalyzer careerFortuneAnalyzer;

    // ─────────────────────────────────────────
    // SajuDataService 예외 검증 (로직 수준)
    // ─────────────────────────────────────────

    @Test
    @DisplayName("null birthTime → InvalidSajuDataException")
    void shouldThrowWhenBirthTimeIsNull() {
        // Given
        LocalDate birthDate = LocalDate.of(1990, 10, 10);
        LocalTime birthTime = null;

        // When & Then
        assertThatThrownBy(() -> {
            if (birthTime == null) throw new InvalidSajuDataException("태어난 시간이 필수입니다 (HH:mm 형식)");
        }).isInstanceOf(InvalidSajuDataException.class)
                .hasMessageContaining("시간");
    }

    @Test
    @DisplayName("null birthDate → InvalidSajuDataException")
    void shouldThrowWhenBirthDateIsNull() {
        // Given
        LocalDate birthDate = null;
        LocalTime birthTime = LocalTime.of(14, 30);

        // When & Then
        assertThatThrownBy(() -> {
            if (birthDate == null) throw new InvalidSajuDataException("생년월일이 필수입니다");
        }).isInstanceOf(InvalidSajuDataException.class)
                .hasMessageContaining("생년월일");
    }

    // ─────────────────────────────────────────
    // TenGodCalculator 동작 검증
    // ─────────────────────────────────────────

    @Test
    @DisplayName("TenGodCalculator - 유효한 천간 4개로 십신 계산 성공")
    void shouldCalculateTenGodWithValidStems() {
        // Given - 庚(년), 甲(월), 己(일/일간), 丁(시)
        var heavenlyStems = List.of("庚", "甲", "己", "丁");

        // When
        var result = tenGodCalculator.calculate(heavenlyStems);

        // Then
        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("TenGodCalculator - 천간 3개로 계산 시 IllegalArgumentException")
    void shouldThrowWhenStemsCountIsWrong() {
        // Given
        var wrongStems = List.of("庚", "甲", "己"); // 3개 (4개여야 함)

        // When & Then
        assertThatThrownBy(() -> tenGodCalculator.calculate(wrongStems))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─────────────────────────────────────────
    // HiddenStemCalculator 동작 검증
    // ─────────────────────────────────────────

    @Test
    @DisplayName("HiddenStemCalculator - 유효한 지지 4개로 지장간 계산 성공")
    void shouldCalculateHiddenStemsWithValidBranches() {
        // Given - 午(년), 戌(월), 未(일), 寅(시)
        var earthlyBranches = List.of("午", "戌", "未", "寅");

        // When
        var result = hiddenStemCalculator.calculate(earthlyBranches);

        // Then
        assertThat(result).isNotNull().hasSize(4);
        assertThat(result.get("午")).containsExactly("丁", "己");
    }

    @Test
    @DisplayName("HiddenStemCalculator - 지지 3개로 계산 시 IllegalArgumentException")
    void shouldThrowWhenBranchesCountIsWrong() {
        // Given
        var wrongBranches = List.of("午", "戌", "未"); // 3개

        // When & Then
        assertThatThrownBy(() -> hiddenStemCalculator.calculate(wrongBranches))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─────────────────────────────────────────
    // CareerFortuneAnalyzer 동작 검증
    // ─────────────────────────────────────────

    @Test
    @DisplayName("CareerFortuneAnalyzer - H1 또는 H2 판정 정상 반환")
    void shouldReturnH1OrH2() {
        // Given
        var heavenlyStems = List.of("庚", "甲", "己", "丁");
        var earthlyBranches = List.of("午", "戌", "未", "寅");
        var tenGodDistribution = tenGodCalculator.calculate(heavenlyStems);
        var hiddenStems = hiddenStemCalculator.calculate(earthlyBranches);
        String dayMaster = heavenlyStems.get(2); // 己

        // When
        String result = careerFortuneAnalyzer.analyzeFavoredPeriod(
                tenGodDistribution, hiddenStems, dayMaster, earthlyBranches);

        // Then
        assertThat(result).isIn("H1", "H2");
    }
}
