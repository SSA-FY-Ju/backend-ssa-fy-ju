package ssafy.SSAju.career.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HiddenStems 값 객체 테스트")
class HiddenStemsTest {

    @Test
    @DisplayName("getByBranch - 존재하는 지지 → 지장간 목록 반환")
    void getByBranch_existingKey_returnsStemList() {
        var stems = new HiddenStems(Map.of("子", List.of("癸"), "丑", List.of("癸", "辛", "己")));

        assertThat(stems.getByBranch("子")).containsExactly("癸");
        assertThat(stems.getByBranch("丑")).containsExactly("癸", "辛", "己");
    }

    @Test
    @DisplayName("getByBranch - 없는 지지 → 빈 리스트 반환")
    void getByBranch_missingKey_returnsEmptyList() {
        var stems = new HiddenStems(Map.of("子", List.of("癸")));

        assertThat(stems.getByBranch("亥")).isEmpty();
    }

    @Test
    @DisplayName("containsStem - 포함된 천간 → true")
    void containsStem_presentStem_returnsTrue() {
        var stems = new HiddenStems(Map.of("子", List.of("癸"), "丑", List.of("癸", "辛", "己")));

        assertThat(stems.containsStem("辛")).isTrue();
    }

    @Test
    @DisplayName("containsStem - 미포함 천간 → false")
    void containsStem_absentStem_returnsFalse() {
        var stems = new HiddenStems(Map.of("子", List.of("癸")));

        assertThat(stems.containsStem("甲")).isFalse();
    }

    @Test
    @DisplayName("asMap - 지지-지장간 맵 반환")
    void asMap_returnsCorrectMap() {
        var data = Map.of("子", List.of("癸"));
        var stems = new HiddenStems(data);

        assertThat(stems.asMap()).containsKey("子");
    }

    @Test
    @DisplayName("toString - 내부 맵의 문자열 표현 반환")
    void toString_delegatesToMap() {
        var stems = new HiddenStems(Map.of("子", List.of("癸")));

        assertThat(stems.toString()).contains("子");
    }
}
