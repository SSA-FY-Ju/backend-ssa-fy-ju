package ssafy.SSAju.career.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenGodDistribution 값 객체 테스트")
class TenGodDistributionTest {

    @Test
    @DisplayName("getScore - 존재하는 십신 → 점수 반환")
    void getScore_existingKey_returnsScore() {
        var dist = new TenGodDistribution(Map.of("정관", 2, "편관", 1));

        assertThat(dist.getScore("정관")).isEqualTo(2);
        assertThat(dist.getScore("편관")).isEqualTo(1);
    }

    @Test
    @DisplayName("getScore - 없는 십신 → 0 반환")
    void getScore_missingKey_returnsZero() {
        var dist = new TenGodDistribution(Map.of("정관", 1));

        assertThat(dist.getScore("비견")).isZero();
    }

    @Test
    @DisplayName("hasHighConfidence - 합계 >= threshold → true")
    void hasHighConfidence_sumAboveThreshold_returnsTrue() {
        var dist = new TenGodDistribution(Map.of("정관", 2, "편관", 1));

        assertThat(dist.hasHighConfidence(3)).isTrue();
        assertThat(dist.hasHighConfidence(4)).isFalse();
    }

    @Test
    @DisplayName("asMap - 불변 맵 반환")
    void asMap_returnsUnmodifiableMap() {
        var dist = new TenGodDistribution(Map.of("정관", 1));

        assertThat(dist.asMap()).containsEntry("정관", 1);
    }

    @Test
    @DisplayName("toString - 내부 맵의 문자열 표현 반환")
    void toString_delegatesToMap() {
        var dist = new TenGodDistribution(Map.of("정관", 1));

        assertThat(dist.toString()).contains("정관");
    }
}
