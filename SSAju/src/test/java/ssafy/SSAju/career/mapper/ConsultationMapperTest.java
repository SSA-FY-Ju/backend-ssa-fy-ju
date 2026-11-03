package ssafy.SSAju.career.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.provider.SajuAnalysisFacade;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 컨설팅 결과 스냅샷 변환 검증.
 *
 * <p>요약 문구는 저장 시점에 한 번만 만들고 조회할 때 다시 만들지 않으므로, 현재 시각이 아니라
 * 요청 시작 시 확정된 연도를 쓰는지 확인한다.
 */
@DisplayName("ConsultationMapper 단위 테스트")
class ConsultationMapperTest {

    private final TenGodCalculator tenGodCalculator = new TenGodCalculator();
    private final SajuAnalysisFacade facade = new SajuAnalysisFacade(
            tenGodCalculator, new HiddenStemCalculator(), new CareerFortuneAnalyzer(tenGodCalculator));
    /** 매퍼의 Clock은 2026년 — 아래 테스트는 2030년을 넘겨 문맥 연도가 쓰이는지 본다. */
    private final ConsultationMapper mapper = new ConsultationMapper(UnifiedAnalysisTestSupport.fixedClock());

    private final ManseData manseData = UnifiedAnalysisTestSupport.manseData();

    @Test
    @DisplayName("만세력과 AI 응답으로 완성 결과 스냅샷을 만든다")
    void shouldBuildResultSnapshot() {
        // Given
        SajuAnalysisFacade.SajuAnalysisContext ctx = facade.analyze(manseData);

        // When
        AnalysisPayload.ConsultationResultData result =
                mapper.toResultSnapshot(manseData, ctx, advice(), "gpt-4o-mini", 2030);

        // Then
        assertThat(result.schemaVersion()).isEqualTo(AnalysisPayload.SCHEMA_VERSION);
        assertThat(result.openaiModelVersion()).isEqualTo("gpt-4o-mini");
        assertThat(result.sajuProfile().dayMaster()).isEqualTo(manseData.dayMaster());
        assertThat(result.sajuProfile().fiveElements()).isEqualTo(manseData.fiveElements());
        assertThat(result.sajuProfile().keyTenGods()).containsExactly("정관");
        assertThat(result.sajuProfile().tenGodCharacteristics()).isNotEmpty();
        assertThat(result.industries()).hasSize(1);
        assertThat(result.favoredPeriod()).isIn("H1", "H2");
    }

    @Test
    @DisplayName("요약은 현재 연도가 아니라 전달받은 분석 연도를 쓴다")
    void shouldUseGivenAnalysisYearForSummary() {
        // When
        String summary = mapper.buildAnalysisSummary(
                manseData.dayMaster(), manseData.tenGodDistribution(), manseData.fiveElements(), "H1", 2030);

        // Then
        assertThat(summary).contains("2030년").doesNotContain("2026년");
    }

    @Test
    @DisplayName("관운 해석이 없으면 요약에 null을 노출하지 않는다")
    void shouldNotExposeNullInSummary() {
        // When
        String summary = mapper.buildAnalysisSummary(
                manseData.dayMaster(), manseData.tenGodDistribution(), manseData.fiveElements(), null, 2030);

        // Then
        assertThat(summary).contains("분석 미포함").doesNotContain("null");
    }

    private CareerAdviceResponse advice() {
        return new CareerAdviceResponse(
                List.of(new CareerAdviceResponse.IndustryRecommendation("IT", "적성", List.of("백엔드"))),
                List.of("면접 팁"), List.of("강점"), List.of("주의"),
                null, null, null, null, null, null, null, null,
                new CareerAdviceResponse.CareerTimeline(2030, Map.of(), List.of(), List.of(), "주의"),
                List.of("정관"), "일간 설명", "오행 설명");
    }
}
