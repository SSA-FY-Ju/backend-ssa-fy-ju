package ssafy.SSAju.career.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.enums.FiveElement;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * HiddenStemCalculator가 반환하는 모든 지장간이 FiveElement.fromStem()으로
 * 정상 변환되는지 검증하는 테스트.
 *
 * 목적: CompatibilityScoreCalculator.countElements() 내부의
 *       try { return FiveElement.fromStem(s); } catch (Exception e) { return null; }
 *       패턴이 실제로 필요한 방어 코드인지 확인한다.
 *
 * 데이터 흐름:
 *   FastAPI → earthlyBranches(지지 4개) → HiddenStemCalculator → HiddenStems(지장간) → countElements()
 *
 * 검증 가설:
 *   HiddenStemCalculator는 10천간(甲乙丙丁戊己庚辛壬癸)만 지장간으로 반환하고,
 *   FiveElement enum은 10천간을 모두 커버하므로 예외가 발생할 수 없다.
 */
@DisplayName("HiddenStem → FiveElement 변환 커버리지 테스트")
class HiddenStemFiveElementCoverageTest {

    private HiddenStemCalculator hiddenStemCalculator;

    @BeforeEach
    void setUp() {
        hiddenStemCalculator = new HiddenStemCalculator();
    }

    // ────────────────────────────────────────────────
    // 1. 핵심 검증: HiddenStemCalculator 출력값이 모두 FiveElement로 변환 가능한가
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("[핵심] 12지지의 모든 지장간이 FiveElement.fromStem()으로 예외 없이 변환된다")
    void allHiddenStemsFromAllBranches_shouldMapToFiveElementWithoutException() {
        // Given: 12지지 전체
        List<String> allBranches = List.of("子", "丑", "寅", "卯", "辰", "巳",
                                           "午", "未", "申", "酉", "戌", "亥");

        // When & Then: 각 지지의 지장간 → FiveElement 변환 시 예외 없어야 함
        for (String branch : allBranches) {
            List<String> stems = hiddenStemCalculator.getHiddenStems(branch);

            for (String stem : stems) {
                assertThatCode(() -> FiveElement.fromStem(stem))
                        .as("지지 [%s]의 지장간 [%s]가 FiveElement로 변환될 수 있어야 합니다", branch, stem)
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    @DisplayName("[핵심] 실제 사주 4개 지지 조합으로 생성된 HiddenStems의 모든 지장간이 FiveElement로 변환된다")
    void hiddenStemsFromFourBranchesCombinations_shouldAllMapToFiveElement() {
        // Given: 다양한 사주 지지 조합
        List<List<String>> testCombinations = List.of(
                List.of("子", "丑", "寅", "卯"),  // 水·土·木·木
                List.of("辰", "巳", "午", "未"),  // 土·火·火·土
                List.of("申", "酉", "戌", "亥"),  // 金·金·土·水
                List.of("子", "午", "卯", "酉"),  // 자오충·묘유충 (강한 충 구성)
                List.of("寅", "申", "巳", "亥"),  // 인신충·사해충 (강한 충 구성)
                List.of("子", "子", "子", "子"),  // 같은 지지 4개 (극단적 케이스)
                List.of("亥", "亥", "午", "午")   // 수화 대립 구성
        );

        for (List<String> branches : testCombinations) {
            // When
            HiddenStems hiddenStems = hiddenStemCalculator.calculate(branches);

            // Then
            hiddenStems.asMap().forEach((branch, stems) ->
                    stems.forEach(stem ->
                            assertThatCode(() -> FiveElement.fromStem(stem))
                                    .as("지지 조합 %s 중 [%s]의 지장간 [%s] 변환 실패", branches, branch, stem)
                                    .doesNotThrowAnyException()
                    )
            );
        }
    }

    // ────────────────────────────────────────────────
    // 2. 10천간 각각의 변환 가능성 확인
    // ────────────────────────────────────────────────

    @ParameterizedTest(name = "천간 [{0}] → FiveElement 변환 성공")
    @ValueSource(strings = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"})
    @DisplayName("10천간 전체가 FiveElement.fromStem()으로 정상 변환된다")
    void allTenHeavenlyStems_shouldMapToFiveElement(String stem) {
        assertThatCode(() -> FiveElement.fromStem(stem))
                .doesNotThrowAnyException();
    }

    // ────────────────────────────────────────────────
    // 3. try-catch가 방어하는 케이스가 실제로 발생하는지 확인
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("[방어 케이스 확인] 지지 문자를 천간 자리에 입력하면 InvalidSajuDataException이 발생한다")
    void earthlyBranchAsInput_shouldThrowInvalidSajuDataException() {
        // 지지(地支) 문자는 FiveElement에 매핑되지 않으므로 예외 발생해야 함
        List<String> earthlyBranches = List.of("子", "丑", "寅", "卯", "辰", "巳",
                                               "午", "未", "申", "酉", "戌", "亥");

        for (String branch : earthlyBranches) {
            assertThatThrownBy(() -> FiveElement.fromStem(branch))
                    .as("지지 [%s]를 천간 자리에 입력 시 예외 발생해야 합니다", branch)
                    .isInstanceOf(InvalidSajuDataException.class)
                    .hasMessageContaining("알 수 없는 천간 값");
        }
    }

    @Test
    @DisplayName("[방어 케이스 확인] HiddenStemCalculator는 지지를 지장간으로 반환하지 않는다")
    void hiddenStemCalculator_neverReturnsEarthlyBranchAsStem() {
        // HiddenStemCalculator가 반환하는 지장간이 지지를 포함하지 않음을 명시적으로 검증
        List<String> allBranches = List.of("子", "丑", "寅", "卯", "辰", "巳",
                                           "午", "未", "申", "酉", "戌", "亥");
        List<String> allTenStems = List.of("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸");

        for (String branch : allBranches) {
            List<String> stems = hiddenStemCalculator.getHiddenStems(branch);

            for (String stem : stems) {
                assertThat(allTenStems)
                        .as("지지 [%s]의 지장간 [%s]는 반드시 10천간 중 하나여야 합니다", branch, stem)
                        .contains(stem);
            }
        }
    }
}
