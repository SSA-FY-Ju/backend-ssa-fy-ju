package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;
import ssafy.SSAju.dto.external.FastAPIResponse;

import java.util.Map;

/**
 * 관운(官運) 분석기: 십신 데이터를 기반으로 상반기(H1)/하반기(H2) 취업 최적 시기를 판정한다.
 * 정관(正官)/편관(偏官)의 강도와 현재 연도 간지를 분석해 H1/H2를 예측한다.
 */
@Component
public class CareerFortuneAnalyzer {

    private static final int BASE_CONFIDENCE = 50;
    private static final int GWAN_BOOST = 15;

    private final TenGodCalculator tenGodCalculator;

    public CareerFortuneAnalyzer(TenGodCalculator tenGodCalculator) {
        this.tenGodCalculator = tenGodCalculator;
    }

    public AnalysisResult analyze(FastAPIResponse sajuData) {
        Map<String, String> tenGods = tenGodCalculator.calculateAll(sajuData);

        long gwanCount = tenGods.values().stream()
                .filter(tg -> tg.contains("관"))
                .count();

        // 관성(정관/편관) 강도로 취업 운 판정
        String favoredPeriod = determineFavoredPeriod(sajuData, gwanCount);
        int confidenceScore = calculateConfidence(gwanCount);
        String reasoning = buildReasoning(tenGods, gwanCount, favoredPeriod);

        return new AnalysisResult(favoredPeriod, confidenceScore, reasoning);
    }

    private String determineFavoredPeriod(FastAPIResponse sajuData, long gwanCount) {
        // 월간(月干)의 십신이 관성이면 상반기 유리
        String monthTenGod = tenGodCalculator.calculate(sajuData.dayStem(), sajuData.monthStem());
        if (monthTenGod.contains("관")) {
            return "H1";
        }
        return gwanCount >= 2 ? "H1" : "H2";
    }

    private int calculateConfidence(long gwanCount) {
        return Math.min(BASE_CONFIDENCE + (int) (gwanCount * GWAN_BOOST), 95);
    }

    private String buildReasoning(Map<String, String> tenGods, long gwanCount, String favoredPeriod) {
        String period = "H1".equals(favoredPeriod) ? "상반기" : "하반기";
        return String.format(
                "관성 수: %d개, 사주 구성: %s — %s 취업 활동이 유리합니다.",
                gwanCount, tenGods, period
        );
    }

    public record AnalysisResult(
            String favoredPeriod,
            int confidenceScore,
            String reasoning
    ) {}
}
