package ssafy.SSAju.career.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.AnalysisTimeContext;
import ssafy.SSAju.career.domain.BirthInfo;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.provider.SajuAnalysisFacade;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.career.validator.RequestValidator;
import ssafy.SSAju.dto.request.CareerTimingRequest;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SajuAnalysisStrategy 단위 테스트")
class SajuAnalysisStrategyTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);

    private final TenGodCalculator tenGodCalculator = new TenGodCalculator();
    private final SajuAnalysisFacade facade = new SajuAnalysisFacade(
            tenGodCalculator, new HiddenStemCalculator(), new CareerFortuneAnalyzer(tenGodCalculator));
    private final SajuAnalysisStrategy strategy = new SajuAnalysisStrategy(facade, new RequestValidator());

    @Test
    @DisplayName("관운 유형만 지원한다")
    void shouldSupportOnlySajuType() {
        assertThat(strategy.supports(AnalysisType.SAJU)).isTrue();
        assertThat(strategy.supports(AnalysisType.CAREER_CONSULTATION)).isFalse();
        assertThat(strategy.supports(AnalysisType.COMPANY_COMPATIBILITY)).isFalse();
        assertThat(strategy.inputType()).isEqualTo(CareerTimingRequest.class);
    }

    @Test
    @DisplayName("요청의 출생정보를 그대로 사용한다")
    void shouldUseRequestedBirthInfo() {
        // When
        BirthInfo birthInfo = strategy.birthInfo(new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME));

        // Then
        assertThat(birthInfo).isEqualTo(new BirthInfo(BIRTH_DATE, BIRTH_TIME));
    }

    @Test
    @DisplayName("저장된 만세력으로 요청별 관운 해석과 입력 스냅샷을 만든다")
    void shouldProduceOutcomeFromStoredManse() {
        // Given
        AnalysisTimeContext time = AnalysisTimeContext.from(UnifiedAnalysisTestSupport.fixedClock());

        // When
        AnalysisOutcome<AnalysisPayload.SajuResultData> outcome = strategy.analyze(
                new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME),
                UnifiedAnalysisTestSupport.manseData(),
                time);

        // Then
        assertThat(outcome.result().favoredPeriod()).isIn("H1", "H2");
        assertThat(outcome.result().confidenceScore()).isBetween(0, 100);
        assertThat(outcome.result().reasoning()).isNotBlank();
        assertThat(outcome.result().schemaVersion()).isEqualTo(AnalysisPayload.SCHEMA_VERSION);

        AnalysisPayload.SajuInput input = (AnalysisPayload.SajuInput) outcome.input();
        assertThat(input.birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(input.birthTime()).isEqualTo(BIRTH_TIME);
        assertThat(input.analysisYear()).isEqualTo(time.year());
        assertThat(input.analysisMonth()).isEqualTo(time.month());
    }

    @Test
    @DisplayName("같은 입력을 반복 분석해도 결과 스냅샷을 그대로 다시 만든다")
    void shouldAnalyzeAgainForRepeatedInput() {
        // Given
        AnalysisTimeContext time = AnalysisTimeContext.from(UnifiedAnalysisTestSupport.fixedClock());
        CareerTimingRequest request = new CareerTimingRequest(BIRTH_DATE, BIRTH_TIME);

        // When
        AnalysisOutcome<AnalysisPayload.SajuResultData> first =
                strategy.analyze(request, UnifiedAnalysisTestSupport.manseData(), time);
        AnalysisOutcome<AnalysisPayload.SajuResultData> second =
                strategy.analyze(request, UnifiedAnalysisTestSupport.manseData(), time);

        // Then: 캐시를 돌려주는 경로가 없으므로 매번 새 결과 객체가 만들어진다
        assertThat(second.result()).isNotSameAs(first.result()).isEqualTo(first.result());
    }

    @Test
    @DisplayName("출생정보가 없는 요청은 검증에서 거부된다")
    void shouldRejectMissingBirthInfo() {
        assertThatThrownBy(() -> strategy.validateInput(new CareerTimingRequest(null, BIRTH_TIME)))
                .isInstanceOf(InvalidSajuDataException.class);
        assertThatThrownBy(() -> strategy.validateInput(new CareerTimingRequest(BIRTH_DATE, null)))
                .isInstanceOf(InvalidSajuDataException.class);
    }

    @Test
    @DisplayName("비어 있거나 범위를 벗어난 결과는 저장 전에 거부된다")
    void shouldRejectInvalidResult() {
        assertThatThrownBy(() -> strategy.validateResult(
                AnalysisPayload.SajuResultData.of(" ", 50, "근거")))
                .isInstanceOf(InvalidSajuDataException.class);
        assertThatThrownBy(() -> strategy.validateResult(
                AnalysisPayload.SajuResultData.of("H1", 101, "근거")))
                .isInstanceOf(InvalidSajuDataException.class);
        assertThatThrownBy(() -> strategy.validateResult(
                AnalysisPayload.SajuResultData.of("H1", 50, "")))
                .isInstanceOf(InvalidSajuDataException.class);
    }
}
