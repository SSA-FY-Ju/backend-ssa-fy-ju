package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.FiveElements;

/**
 * 직군별 역할 적합도 점수 계산을 담당합니다.
 *
 * <p>주 역할과 보조 역할 점수를 오행 분포로부터 산정합니다.
 * 점수 산정 기획이 변경될 경우 이 클래스만 수정합니다.
 */
@Component
public class RoleCompatibilityCalculator {

    /**
     * 주 역할(전문가) 점수 = 주 오행 보유 수 × 가중치 + 기본점수.
     */
    public int calculatePrimary(FiveElements elements, JobCategoryEnum category) {
        int raw = elements.getCount(category.getPrimaryElement())
                * AnalysisConstants.PRIMARY_ROLE_SCORE_MULTIPLIER
                + AnalysisConstants.PRIMARY_ROLE_SCORE_BASE;
        return Math.min(raw, AnalysisConstants.MAX_SCORE);
    }

    /**
     * 보조 역할(리드) 점수 = 주 역할 점수에서 페널티 차감.
     */
    public int calculateSecondary(int primaryScore) {
        return Math.max(primaryScore - AnalysisConstants.SECONDARY_ROLE_SCORE_PENALTY, AnalysisConstants.MIN_SCORE);
    }
}
