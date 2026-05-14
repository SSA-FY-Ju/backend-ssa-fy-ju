package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;

/**
 * 월별 운세 점수 계산을 담당합니다.
 *
 * <p>계절 오행 보유 수를 입력받아 점수를 산정합니다.
 * 점수 산정 기획이 변경될 경우 이 클래스만 수정합니다.
 */
@Component
public class ForecastScoreCalculator {

    /**
     * 해당 계절 오행 보유 수를 기반으로 운세 점수를 계산합니다.
     *
     * <ul>
     *   <li>elementCount = 0 → 40점 (CAUTION 경계)</li>
     *   <li>elementCount = 1 → 50점 (NORMAL 시작)</li>
     *   <li>elementCount ≥ 4 → 최대 80점 (LUCKY)</li>
     * </ul>
     */
    public int calculate(int elementCount) {
        int raw = AnalysisConstants.MEDIUM_COMPATIBILITY_THRESHOLD
                + (elementCount - 1) * AnalysisConstants.FORECAST_SCORE_STEP;
        return Math.min(Math.max(raw, AnalysisConstants.MIN_SCORE), AnalysisConstants.MAX_SCORE);
    }
}
