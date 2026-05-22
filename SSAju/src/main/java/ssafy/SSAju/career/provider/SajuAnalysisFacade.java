package ssafy.SSAju.career.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.career.enums.SajuPillarIndex;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.dto.external.FastAPIResponse;

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
