package ssafy.SSAju.career.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.SSAju.dto.external.CareerAdviceResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsultationResultConverter 직렬화/역직렬화 라운드트립")
class ConsultationResultConverterTest {

    private final ConsultationResultConverter converter = new ConsultationResultConverter();

    @Test
    @DisplayName("정상 데이터 라운드트립 → 동일한 값 복원")
    void shouldRoundTrip_WithNormalData() {
        CareerAdviceResponse advice = new CareerAdviceResponse(
                List.of(new CareerAdviceResponse.IndustryRecommendation("IT", "적성 부합", List.of("백엔드"))),
                List.of("면접 팁1"),
                List.of("강점1"),
                List.of("유의사항1"),
                new CareerAdviceResponse.WealthStyle("근로소득", "저축 우선", "안정형", "부업"),
                null, null, null, null, null, null, null, null,
                List.of("정관", "편관"),
                "己土 - 수용적 성향",
                "金 강세"
        );

        String json = converter.convertToDatabaseColumn(advice);
        CareerAdviceResponse restored = converter.convertToEntityAttribute(json);

        assertThat(restored.industries()).hasSize(1);
        assertThat(restored.industries().getFirst().name()).isEqualTo("IT");
        assertThat(restored.interviewTips()).containsExactly("면접 팁1");
        assertThat(restored.wealthStyle().incomeSource()).isEqualTo("근로소득");
        assertThat(restored.dayMasterDescription()).isEqualTo("己土 - 수용적 성향");
        assertThat(restored.fiveElementsAnalysis()).isEqualTo("金 강세");
        assertThat(restored.keyTenGods()).containsExactly("정관", "편관");
    }

    @Test
    @DisplayName("null 속성 → null 컬럼 값")
    void shouldReturnNull_ForNullAttribute() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
