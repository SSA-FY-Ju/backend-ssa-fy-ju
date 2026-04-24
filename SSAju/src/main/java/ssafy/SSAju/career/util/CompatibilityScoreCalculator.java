package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;
import ssafy.SSAju.dto.external.FastAPIResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 기업-사용자 궁합 점수 계산기.
 * 사용자 사주 오행과 기업 설립일 사주 오행의 상생/상극 관계로 점수를 산출한다.
 */
@Component
public class CompatibilityScoreCalculator {

    // 상생(相生) 관계: key가 target을 생(生)함
    private static final Map<String, String> PRODUCES = Map.of(
            "木", "火", "火", "土", "土", "金", "金", "水", "水", "木"
    );

    // 상극(相剋) 관계: key가 target을 극(剋)함
    private static final Map<String, String> CONTROLS = Map.of(
            "木", "土", "土", "水", "水", "火", "火", "金", "金", "木"
    );

    public CompatibilityResult calculate(FastAPIResponse userSaju, FastAPIResponse companySaju) {
        int score = computeScore(userSaju, companySaju);
        String confidenceLevel = toConfidenceLevel(score);
        List<String> recommendedRoles = suggestRoles(userSaju, score);

        return new CompatibilityResult(score, confidenceLevel, recommendedRoles);
    }

    private int computeScore(FastAPIResponse user, FastAPIResponse company) {
        int score = 50; // 기본 점수

        String userDayStem = user.dayStem();
        String companyDayStem = company.dayStem();

        if (userDayStem != null && companyDayStem != null) {
            // 상생이면 +15, 상극이면 -15
            if (companyDayStem.equals(PRODUCES.get(userDayStem))) {
                score += 15;
            } else if (companyDayStem.equals(CONTROLS.get(userDayStem))) {
                score -= 15;
            }
        }

        // 오행 분포 비교 보너스 (최대 ±20)
        score += calculateElementBonus(user, company);

        return Math.max(0, Math.min(100, score));
    }

    private int calculateElementBonus(FastAPIResponse user, FastAPIResponse company) {
        String[] userStems = { user.yearStem(), user.monthStem(), user.dayStem(), user.hourStem() };
        String[] companyStems = { company.yearStem(), company.monthStem(), company.dayStem(), company.hourStem() };

        long matchCount = 0;
        for (int i = 0; i < 4; i++) {
            String u = userStems[i];
            String c = companyStems[i];
            if (u != null && c != null && u.equals(c)) {
                matchCount++;
            }
        }
        return (int) (matchCount * 5);
    }

    private String toConfidenceLevel(int score) {
        if (score >= 70) return "HIGH";
        if (score >= 40) return "MEDIUM";
        return "LOW";
    }

    private List<String> suggestRoles(FastAPIResponse userSaju, int score) {
        List<String> roles = new ArrayList<>();
        if (score >= 70) {
            roles.add("핵심 직무 (전략/기획)");
            roles.add("관리직");
        } else if (score >= 40) {
            roles.add("실무 직무");
            roles.add("협력 부서");
        } else {
            roles.add("지원 부서");
        }
        return roles;
    }

    public record CompatibilityResult(
            int score,
            String confidenceLevel,
            List<String> recommendedRoles
    ) {}
}
