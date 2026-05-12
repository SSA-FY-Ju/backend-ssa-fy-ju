package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.FiveElements;
import ssafy.SSAju.career.enums.FiveElement;
import ssafy.SSAju.dto.response.CompatibilityResponse;

/**
 * 사용자 오행(五行) 분포와 직군 오행을 비교하여 직군 적합도를 분석합니다.
 *
 * <p>문구(시너지/경고 텍스트)는 정적 상수로 분리하여 로직과 분리합니다.
 * 오행 상극 관계는 {@link FiveElement#opposing()}을 통해 Enum에서 관리합니다.
 */
@Component
public class JobRoleAnalyzer {

    // ─────────────────────────────────────────
    // 문구 상수 (비즈니스 로직과 텍스트 분리)
    // ─────────────────────────────────────────

    private static final String SYNERGY_HIGH_FMT =
            "%s 직군의 핵심 오행인 '%s(이/가)' 사용자 사주에 강하게 나타나 뛰어난 적성을 보입니다. " +
            "%s 기운이 %s 분야의 성공을 뒷받침합니다.";

    private static final String SYNERGY_MED_FMT =
            "%s 직군에 필요한 '%s' 기운이 사용자 사주에 포함되어 있어 기본적인 적성을 갖추고 있습니다.";

    private static final String SYNERGY_LOW_FMT =
            "%s 직군의 핵심 오행인 '%s'이 사용자 사주에 부족하지만, 보완적 노력으로 성장이 가능합니다.";

    private static final String WARNING_HIGH_FMT =
            "사용자 사주의 강한 '%s' 기운이 %s 직군의 '%s' 흐름과 상극 관계에 있어 " +
            "업무 방식의 차이로 인한 어려움이 발생할 수 있습니다.";

    private static final String WARNING_DEFAULT_FMT =
            "%s 직군의 특성상 지속적인 학습과 역량 개발이 필요합니다.";

    /**
     * 사용자 오행 분포와 직군 오행을 비교하여 직군 적합도를 분석합니다.
     */
    public CompatibilityResponse.TargetRoleAnalysis analyze(FiveElements userFiveElements,
                                                             JobCategoryEnum category) {
        int primaryCount = userFiveElements.getCount(category.getPrimaryElement());
        int secondaryCount = userFiveElements.getCount(category.getSecondaryElement());

        int matchScore = calculateMatchScore(primaryCount, secondaryCount);
        String synergy = buildSynergyText(category, primaryCount, secondaryCount);
        String warning = buildWarningText(category, userFiveElements);

        return new CompatibilityResponse.TargetRoleAnalysis(matchScore, synergy, warning);
    }

    private int calculateMatchScore(int primaryCount, int secondaryCount) {
        int score = primaryCount * AnalysisConstants.PRIMARY_MATCH_WEIGHT
                + secondaryCount * AnalysisConstants.SECONDARY_MATCH_WEIGHT;
        return Math.min(score, AnalysisConstants.MAX_SCORE);
    }

    private String buildSynergyText(JobCategoryEnum category, int primaryCount, int secondaryCount) {
        if (primaryCount >= 2) {
            return String.format(SYNERGY_HIGH_FMT,
                    category.getDisplayName(), category.getPrimaryElement(),
                    category.getPrimaryElement(), category.getDisplayName());
        }
        if (primaryCount >= 1 || secondaryCount >= 1) {
            return String.format(SYNERGY_MED_FMT,
                    category.getDisplayName(), category.getPrimaryElement());
        }
        return String.format(SYNERGY_LOW_FMT,
                category.getDisplayName(), category.getPrimaryElement());
    }

    private String buildWarningText(JobCategoryEnum category, FiveElements userFiveElements) {
        // FiveElement Enum으로 상극 오행 조회 (알 수 없는 값이면 즉시 실패)
        String opposingSymbol = FiveElement.fromSymbol(category.getPrimaryElement())
                .opposing()
                .getSymbol();
        int opposingCount = userFiveElements.getCount(opposingSymbol);

        if (opposingCount >= AnalysisConstants.STRONG_OPPOSING_THRESHOLD) {
            return String.format(WARNING_HIGH_FMT,
                    opposingSymbol, category.getDisplayName(), category.getPrimaryElement());
        }
        return String.format(WARNING_DEFAULT_FMT, category.getDisplayName());
    }
}
