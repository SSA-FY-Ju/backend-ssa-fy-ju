package ssafy.SSAju.career.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FiveElements 값 객체 테스트")
class FiveElementsTest {

    @Test
    @DisplayName("getCount - 존재하는 오행 → 개수 반환")
    void getCount_existingKey_returnsCount() {
        var elements = new FiveElements(Map.of("木", 2, "火", 1));

        assertThat(elements.getCount("木")).isEqualTo(2);
        assertThat(elements.getCount("火")).isEqualTo(1);
    }

    @Test
    @DisplayName("getCount - 없는 오행 → 0 반환")
    void getCount_missingKey_returnsZero() {
        var elements = new FiveElements(Map.of("木", 1));

        assertThat(elements.getCount("水")).isZero();
    }

    @Test
    @DisplayName("asMap - 불변 맵 반환")
    void asMap_returnsUnmodifiableMap() {
        var elements = new FiveElements(Map.of("木", 1, "火", 2));

        assertThat(elements.asMap()).containsEntry("木", 1).containsEntry("火", 2);
        assertThatThrownBy(() -> elements.asMap().put("土", 3))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
