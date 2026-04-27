package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 관운(官運) 분석기
 * 십신 분포와 지장간 데이터를 기반으로 취업 유리 시기(H1/H2)를 판정합니다.
 *
 * 판정 기준:
 * - 정관(正官) / 편관(偏官) 강도를 1차 판정 기준으로 사용
 * - 지장간 내 정관/편관 포함 비율을 2차 보정 기준으로 사용
 * - 월지(月支)의 관성 강도를 중심 지표로 활용 (월지 = earthlyBranches[1])
 */
@Component
public class CareerFortuneAnalyzer {

    private static final List<String> OFFICER_GODS = List.of("정관", "편관");
    private static final List<String> FAVORABLE_OFFICER_BRANCHES_H1 = List.of("子", "丑", "寅", "卯", "辰", "巳");
    private static final List<String> FAVORABLE_OFFICER_BRANCHES_H2 = List.of("午", "未", "申", "酉", "戌", "亥");

    private final TenGodCalculator tenGodCalculator;
    private final HiddenStemCalculator hiddenStemCalculator;

    public CareerFortuneAnalyzer(TenGodCalculator tenGodCalculator, HiddenStemCalculator hiddenStemCalculator) {
        this.tenGodCalculator = tenGodCalculator;
        this.hiddenStemCalculator = hiddenStemCalculator;
    }

    /**
     * H1(상반기) vs H2(하반기) 취업 유리 시기를 판정합니다.
     *
     * @param tenGodDistribution 십신 분포 Map
     * @param hiddenStems 지장간 Map (지지 → 지장간 목록)
     * @param dayMaster 일간 (천간 인덱스 2)
     * @param earthlyBranches 지지 목록 (年月日時)
     * @return "H1" 또는 "H2"
     */
    public String analyzeFavoredPeriod(Map<String, Integer> tenGodDistribution,
                                        Map<String, List<String>> hiddenStems,
                                        String dayMaster,
                                        List<String> earthlyBranches) {
        int officerScore = calculateOfficerScore(tenGodDistribution, hiddenStems, dayMaster);
        String monthBranch = earthlyBranches.get(1);

        if (FAVORABLE_OFFICER_BRANCHES_H1.contains(monthBranch)) {
            return officerScore >= 0 ? "H1" : "H2";
        }
        return officerScore >= 0 ? "H2" : "H1";
    }

    /**
     * 관성 강도 점수를 계산합니다. 양수이면 상반기, 음수이면 하반기 유리.
     */
    private int calculateOfficerScore(Map<String, Integer> tenGodDistribution,
                                       Map<String, List<String>> hiddenStems,
                                       String dayMaster) {
        int score = 0;

        // 천간 관성 점수
        for (String god : OFFICER_GODS) {
            score += tenGodDistribution.getOrDefault(god, 0) * 2;
        }

        // 지장간 관성 점수 (보정)
        for (Map.Entry<String, List<String>> entry : hiddenStems.entrySet()) {
            for (String hiddenStem : entry.getValue()) {
                String god = tenGodCalculator.getTenGod(dayMaster, hiddenStem);
                if (OFFICER_GODS.contains(god)) {
                    score += 1;
                }
            }
        }
        return score;
    }

    /**
     * 신뢰도 점수(0~100)를 계산합니다.
     */
    public int calculateConfidenceScore(Map<String, Integer> tenGodDistribution,
                                         Map<String, List<String>> hiddenStems,
                                         String dayMaster) {
        int officerCount = OFFICER_GODS.stream()
                .mapToInt(god -> tenGodDistribution.getOrDefault(god, 0))
                .sum();

        int hiddenOfficerCount = 0;
        for (Map.Entry<String, List<String>> entry : hiddenStems.entrySet()) {
            for (String stem : entry.getValue()) {
                String god = tenGodCalculator.getTenGod(dayMaster, stem);
                if (OFFICER_GODS.contains(god)) {
                    hiddenOfficerCount++;
                }
            }
        }

        int rawScore = officerCount * 20 + hiddenOfficerCount * 5;
        return Math.min(rawScore + 40, 100);
    }
}
