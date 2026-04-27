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
    // 관성을 설기(洩氣)시키는 십신 → 감점 대상
    private static final List<String> WEAKENING_GODS = List.of("식신", "상관");
    // 비겁 과다도 관성 부담 요인
    private static final List<String> COMPETING_GODS = List.of("비견", "겁재");
    private static final List<String> FAVORABLE_OFFICER_BRANCHES_H1 = List.of("子", "丑", "寅", "卯", "辰", "巳");

    private final TenGodCalculator tenGodCalculator;

    public CareerFortuneAnalyzer(TenGodCalculator tenGodCalculator) {
        this.tenGodCalculator = tenGodCalculator;
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
        if (tenGodDistribution == null) {
            throw new IllegalArgumentException("십신 분포 데이터가 null입니다.");
        }
        if (hiddenStems == null) {
            throw new IllegalArgumentException("지장간 데이터가 null입니다.");
        }
        if (dayMaster == null || dayMaster.isBlank()) {
            throw new IllegalArgumentException("일간이 null이거나 비어있습니다.");
        }
        if (earthlyBranches == null || earthlyBranches.size() != 4) {
            throw new IllegalArgumentException("지지 목록은 정확히 4개(年月日時)여야 합니다.");
        }
        int officerScore = calculateOfficerScore(tenGodDistribution, hiddenStems, dayMaster);
        String monthBranch = earthlyBranches.get(1);

        if (FAVORABLE_OFFICER_BRANCHES_H1.contains(monthBranch)) {
            return officerScore >= 0 ? "H1" : "H2";
        }
        return officerScore >= 0 ? "H2" : "H1";
    }

    /**
     * 관성 강도 점수를 계산합니다. 양수이면 상반기, 음수이면 하반기 유리.
     * - 정관·편관: 가점 (관성 강도)
     * - 식신·상관: 감점 (관성 설기)
     * - 비겁 2개 이상: 감점 (관성 부담)
     */
    private int calculateOfficerScore(Map<String, Integer> tenGodDistribution,
                                       Map<String, List<String>> hiddenStems,
                                       String dayMaster) {
        int score = 0;

        for (Map.Entry<String, Integer> entry : tenGodDistribution.entrySet()) {
            if (OFFICER_GODS.contains(entry.getKey())) {
                score += entry.getValue() * 20;
            }
            if (WEAKENING_GODS.contains(entry.getKey())) {
                score -= entry.getValue() * 15;
            }
            if (COMPETING_GODS.contains(entry.getKey()) && entry.getValue() >= 2) {
                score -= entry.getValue() * 5;
            }
        }

        // 지장간 기반 보정 (가점/감점 동일 적용)
        for (List<String> stems : hiddenStems.values()) {
            for (String stem : stems) {
                String tenGod = tenGodCalculator.getTenGod(dayMaster, stem);
                if (OFFICER_GODS.contains(tenGod)) {
                    score += 5;
                } else if (WEAKENING_GODS.contains(tenGod)) {
                    score -= 3;
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
