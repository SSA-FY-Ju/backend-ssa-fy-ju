package ssafy.SSAju.career.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.SSAju.career.domain.TenGodHiddenStemAnalysis;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenGodHiddenStemConverter 직렬화/역직렬화 라운드트립")
class TenGodHiddenStemConverterTest {

    private final TenGodHiddenStemConverter converter = new TenGodHiddenStemConverter();

    @Test
    @DisplayName("정상 데이터 라운드트립 → 동일한 값 복원")
    void shouldRoundTrip_WithNormalData() {
        TenGodHiddenStemAnalysis analysis = new TenGodHiddenStemAnalysis(
                Map.of("비견", 2, "겁재", 1),
                Map.of("년주", List.of("갑", "을"), "일주", List.of("병")));

        String json = converter.convertToDatabaseColumn(analysis);
        TenGodHiddenStemAnalysis restored = converter.convertToEntityAttribute(json);

        assertThat(restored.tenGods()).isEqualTo(analysis.tenGods());
        assertThat(restored.hiddenStems()).isEqualTo(analysis.hiddenStems());
    }

    @Test
    @DisplayName("빈 계산 결과 → 키는 남고 빈 객체로 직렬화")
    void shouldSerializeEmptyMaps_AsEmptyObjects() {
        TenGodHiddenStemAnalysis analysis = new TenGodHiddenStemAnalysis(Map.of(), Map.of());

        String json = converter.convertToDatabaseColumn(analysis);

        assertThat(json).contains("\"tenGods\"").contains("\"hiddenStems\"");
        TenGodHiddenStemAnalysis restored = converter.convertToEntityAttribute(json);
        assertThat(restored.tenGods()).isEmpty();
        assertThat(restored.hiddenStems()).isEmpty();
    }

    @Test
    @DisplayName("null 속성 → null 컬럼 값")
    void shouldReturnNull_ForNullAttribute() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
