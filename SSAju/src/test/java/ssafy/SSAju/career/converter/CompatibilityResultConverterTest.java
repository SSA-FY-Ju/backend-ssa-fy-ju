package ssafy.SSAju.career.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.enums.ForecastStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CompatibilityResultConverter 직렬화/역직렬화 라운드트립")
class CompatibilityResultConverterTest {

    private final CompatibilityResultConverter converter = new CompatibilityResultConverter();

    @Test
    @DisplayName("정상 데이터 라운드트립 → 동일한 값 복원")
    void shouldRoundTrip_WithNormalData() {
        CompatibilityAnalysisData data = new CompatibilityAnalysisData(
                new CompatibilityAnalysisData.RoleAnalysis(88, "시너지 설명", "주의 사항"),
                new CompatibilityAnalysisData.FiveElementsInfo(
                        Map.of("木", 2, "火", 1), Map.of("金", 3), "오행 시너지"),
                new CompatibilityAnalysisData.ScoreBreakdown(75, 80, 70),
                new CompatibilityAnalysisData.StrategyInfo(
                        List.of("키워드1"), "약점 보완 전략", List.of("월요일"), "오전"),
                List.of(new CompatibilityAnalysisData.InterviewQuestion("질문1", "의도1")),
                List.of(new CompatibilityAnalysisData.RoleCompatibility("백엔드", 90, "적합", "PRIMARY")),
                List.of(new CompatibilityAnalysisData.MonthlyForecast(1, 85, ForecastStatus.LUCKY, "1월 조언")),
                List.of("유의사항1")
        );

        String json = converter.convertToDatabaseColumn(data);
        CompatibilityAnalysisData restored = converter.convertToEntityAttribute(json);

        assertThat(restored.roleAnalysis().matchScore()).isEqualTo(88);
        assertThat(restored.fiveElements().userDistribution()).isEqualTo(Map.of("木", 2, "火", 1));
        assertThat(restored.breakdown().characterMatch()).isEqualTo(75);
        assertThat(restored.strategy().keywords()).containsExactly("키워드1");
        assertThat(restored.questions()).hasSize(1);
        assertThat(restored.roles().getFirst().roleName()).isEqualTo("백엔드");
        assertThat(restored.forecasts().getFirst().status()).isEqualTo(ForecastStatus.LUCKY);
        assertThat(restored.cautions()).containsExactly("유의사항1");
    }

    @Test
    @DisplayName("null 속성 → null 컬럼 값")
    void shouldReturnNull_ForNullAttribute() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
