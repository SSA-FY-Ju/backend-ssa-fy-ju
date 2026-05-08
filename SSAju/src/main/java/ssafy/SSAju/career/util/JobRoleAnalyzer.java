package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.FiveElements;
import ssafy.SSAju.dto.response.CompatibilityResponse;

@Component
public class JobRoleAnalyzer {

    private static final int PRIMARY_MATCH_SCORE = 40;
    private static final int SECONDARY_MATCH_SCORE = 20;
    private static final int MAX_SCORE = 100;

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
        int score = primaryCount * PRIMARY_MATCH_SCORE + secondaryCount * SECONDARY_MATCH_SCORE;
        return Math.min(score, MAX_SCORE);
    }

    private String buildSynergyText(JobCategoryEnum category, int primaryCount, int secondaryCount) {
        if (primaryCount >= 2) {
            return String.format(
                    "%s 직군의 핵심 오행인 '%s(이/가)' 사용자 사주에 강하게 나타나 뛰어난 적성을 보입니다. " +
                    "%s 기운이 %s 분야의 성공을 뒷받침합니다.",
                    category.getDisplayName(), category.getPrimaryElement(),
                    category.getPrimaryElement(), category.getDisplayName());
        }
        if (primaryCount >= 1 || secondaryCount >= 1) {
            return String.format(
                    "%s 직군에 필요한 '%s' 기운이 사용자 사주에 포함되어 있어 기본적인 적성을 갖추고 있습니다.",
                    category.getDisplayName(), category.getPrimaryElement());
        }
        return String.format(
                "%s 직군의 핵심 오행인 '%s'이 사용자 사주에 부족하지만, 보완적 노력으로 성장이 가능합니다.",
                category.getDisplayName(), category.getPrimaryElement());
    }

    private String buildWarningText(JobCategoryEnum category, FiveElements userFiveElements) {
        String opposing = getOpposingElement(category.getPrimaryElement());
        int opposingCount = userFiveElements.getCount(opposing);

        if (opposingCount >= 2) {
            return String.format(
                    "사용자 사주의 강한 '%s' 기운이 %s 직군의 '%s' 흐름과 상극 관계에 있어 " +
                    "업무 방식의 차이로 인한 어려움이 발생할 수 있습니다.",
                    opposing, category.getDisplayName(), category.getPrimaryElement());
        }
        return String.format(
                "%s 직군의 특성상 지속적인 학습과 역량 개발이 필요합니다.",
                category.getDisplayName());
    }

    private String getOpposingElement(String element) {
        return switch (element) {
            case "木" -> "金";
            case "金" -> "木";
            case "火" -> "水";
            case "水" -> "火";
            case "土" -> "木";
            default -> "水";
        };
    }
}
