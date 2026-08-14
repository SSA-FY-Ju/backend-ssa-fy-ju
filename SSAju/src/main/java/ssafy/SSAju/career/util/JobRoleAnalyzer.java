package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.FiveElements;

/**
 * 사용자 오행(五行) 분포와 직군 오행을 비교하여 직군 매칭 점수를 계산합니다.
 *
 * <p>시너지/경고 문구는 더 이상 이 클래스가 생성하지 않는다 — AI 응답
 * ({@code CompatibilityNarrativeResponse.roleSynergy/roleWarning})으로 대체되었다.
 */
@Component
public class JobRoleAnalyzer {

    /**
     * 사용자 오행 분포와 직군 오행을 비교하여 직군 매칭 점수를 계산합니다.
     */
    public int analyze(FiveElements userFiveElements, JobCategoryEnum category) {
        int primaryCount = userFiveElements.getCount(category.getPrimaryElement());
        int secondaryCount = userFiveElements.getCount(category.getSecondaryElement());
        return calculateMatchScore(primaryCount, secondaryCount);
    }

    private int calculateMatchScore(int primaryCount, int secondaryCount) {
        int score = primaryCount * AnalysisConstants.PRIMARY_MATCH_WEIGHT
                + secondaryCount * AnalysisConstants.SECONDARY_MATCH_WEIGHT;
        return Math.min(score, AnalysisConstants.MAX_SCORE);
    }
}
