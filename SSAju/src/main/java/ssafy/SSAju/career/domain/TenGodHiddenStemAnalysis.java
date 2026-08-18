package ssafy.SSAju.career.domain;

import java.util.List;
import java.util.Map;

/**
 * {@code SajuResult.tenGodHiddenStemAnalysis} JSON 컬럼에 저장되는 값 객체.
 *
 * <p>{@link TenGodDistribution#asMap()}과 {@link HiddenStems#asMap()}을 하나의 구조로 묶는다.
 * 계산 결과가 비어 있어도 {@code null}이 아닌 빈 맵으로 채워, JSON 직렬화 시 키 자체는 항상 남는다
 * ({@code {"tenGods": {}, "hiddenStems": {}}}).
 */
public record TenGodHiddenStemAnalysis(
        Map<String, Integer> tenGods,
        Map<String, List<String>> hiddenStems
) {
    public static TenGodHiddenStemAnalysis of(TenGodDistribution tenGodDistribution, HiddenStems hiddenStems) {
        Map<String, Integer> tenGods = tenGodDistribution == null ? Map.of() : tenGodDistribution.asMap();
        Map<String, List<String>> stems = hiddenStems == null ? Map.of() : hiddenStems.asMap();
        return new TenGodHiddenStemAnalysis(tenGods, stems);
    }
}
