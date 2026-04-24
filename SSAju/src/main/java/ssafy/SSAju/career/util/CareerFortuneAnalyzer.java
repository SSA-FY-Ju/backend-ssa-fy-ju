package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.enums.FavoredPeriod;
import ssafy.SSAju.dto.external.FastAPIResponse;

import java.util.Map;

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

        FavoredPeriod favoredPeriod = determineFavoredPeriod(sajuData, gwanCount);
        int confidenceScore = calculateConfidence(gwanCount);
        String reasoning = buildReasoning(tenGods, gwanCount, favoredPeriod);

        return new AnalysisResult(favoredPeriod, confidenceScore, reasoning);
    }

    private FavoredPeriod determineFavoredPeriod(FastAPIResponse sajuData, long gwanCount) {
        // 월간(月干)의 십신이 관성이면 상반기 유리
        String monthTenGod = tenGodCalculator.calculate(sajuData.dayStem(), sajuData.monthStem());
        if (monthTenGod.contains("관")) {
            return FavoredPeriod.H1;
        }
        return gwanCount >= 2 ? FavoredPeriod.H1 : FavoredPeriod.H2;
    }

    private int calculateConfidence(long gwanCount) {
        return Math.min(BASE_CONFIDENCE + (int) (gwanCount * GWAN_BOOST), 95);
    }

    private String buildReasoning(Map<String, String> tenGods, long gwanCount, FavoredPeriod favoredPeriod) {
        return String.format(
                "관성 수: %d개, 사주 구성: %s — %s 취업 활동이 유리합니다.",
                gwanCount, tenGods, favoredPeriod.displayName()
        );
    }

    public record AnalysisResult(
            FavoredPeriod favoredPeriod,
            int confidenceScore,
            String reasoning
    ) {}
}
