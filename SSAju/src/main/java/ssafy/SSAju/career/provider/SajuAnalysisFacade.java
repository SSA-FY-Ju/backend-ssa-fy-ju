package ssafy.SSAju.career.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.enums.SajuPillarIndex;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.util.List;

/**
 * 사주 분석 공통 계산 파사드.
 *
 * ConsultationService, CareerFortuneService에서 중복되던
 * tenGod·hiddenStem·dayMaster·favoredPeriod·confidenceScore·reasoning 계산을 통합합니다.
 */
@Component
@RequiredArgsConstructor
public class SajuAnalysisFacade {

    private final TenGodCalculator tenGodCalculator;
    private final HiddenStemCalculator hiddenStemCalculator;
    private final CareerFortuneAnalyzer careerFortuneAnalyzer;

    public record SajuAnalysisContext(
            String dayMaster,
            TenGodDistribution tenGodDistribution,
            HiddenStems hiddenStems,
            String favoredPeriod,
            int confidenceScore,
            String reasoning
    ) {}

    /**
     * 저장된 만세력으로 이번 요청의 관운 해석을 계산한다.
     *
     * <p>십성·지장간은 만세력에 이미 들어 있으므로 다시 계산하지 않고, 요청마다 달라질 수 있는
     * 최종 해석(favoredPeriod·confidenceScore·reasoning)만 새로 만든다 — 그래서 이 값들은
     * 만세력에 저장하지 않고 각 분석 결과 스냅샷에만 남는다.
     */
    public SajuAnalysisContext analyze(ManseData manseData) {
        if (manseData == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.MANSE_DATA_REQUIRED.getMessage());
        }
        TenGodDistribution tenGodDistribution = manseData.tenGodDistribution();
        HiddenStems hiddenStems = manseData.hiddenStems();
        String dayMaster = manseData.dayMaster();

        String favoredPeriod = careerFortuneAnalyzer.analyzeFavoredPeriod(
                tenGodDistribution, hiddenStems, dayMaster, manseData.earthlyBranches());
        int confidenceScore = careerFortuneAnalyzer.calculateConfidenceScore(
                tenGodDistribution, hiddenStems, dayMaster);
        String reasoning = careerFortuneAnalyzer.buildReasoning(favoredPeriod, tenGodDistribution);

        return new SajuAnalysisContext(dayMaster, tenGodDistribution, hiddenStems,
                favoredPeriod, confidenceScore, reasoning);
    }

    public SajuAnalysisContext analyze(FastAPIResponse sajuData) {
        List<String> heavenlyStems = sajuData.heavenlyStems();
        List<String> earthlyBranches = sajuData.earthlyBranches();

        TenGodDistribution tenGodDistribution = tenGodCalculator.calculate(heavenlyStems);
        HiddenStems hiddenStems = hiddenStemCalculator.calculate(earthlyBranches);
        String dayMaster = heavenlyStems.get(SajuPillarIndex.DAY_INDEX);
        String favoredPeriod = careerFortuneAnalyzer.analyzeFavoredPeriod(
                tenGodDistribution, hiddenStems, dayMaster, earthlyBranches);
        int confidenceScore = careerFortuneAnalyzer.calculateConfidenceScore(
                tenGodDistribution, hiddenStems, dayMaster);
        String reasoning = careerFortuneAnalyzer.buildReasoning(favoredPeriod, tenGodDistribution);

        return new SajuAnalysisContext(dayMaster, tenGodDistribution, hiddenStems,
                favoredPeriod, confidenceScore, reasoning);
    }
}
