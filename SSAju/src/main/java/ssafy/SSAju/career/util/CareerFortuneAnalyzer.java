package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.enums.SajuPillarIndex;
import ssafy.SSAju.career.enums.TenGodConstants;
import ssafy.SSAju.exception.InvalidSajuDataException;

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

    // 관성 십신: 정관, 편관
    private static final List<String> OFFICER_GODS = List.of(
            TenGodConstants.CHIEF_OFFICER.getName(),
            TenGodConstants.SIDE_OFFICER.getName()
    );

    // 관성을 설기(洩氣)시키는 십신 → 감점 대상: 식신, 상관
    private static final List<String> WEAKENING_GODS = List.of(
            TenGodConstants.FOOD_GOD.getName(),
            TenGodConstants.INJURING_OFFICER.getName()
    );

    // 비겁 과다도 관성 부담 요인: 비견, 겁재
    private static final List<String> COMPETING_GODS = List.of(
            TenGodConstants.COMPARING_FRIEND.getName(),
            TenGodConstants.ROBBING_WEALTH.getName()
    );

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
    public String analyzeFavoredPeriod(TenGodDistribution tenGodDistribution,
                                        HiddenStems hiddenStems,
                                        String dayMaster,
                                        List<String> earthlyBranches) {
        if (tenGodDistribution == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.TEN_GOD_DISTRIBUTION_NULL.getMessage());
        }
        if (hiddenStems == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.HIDDEN_STEM_DATA_NULL.getMessage());
        }
        if (dayMaster == null || dayMaster.isBlank()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.DAY_MASTER_NULL.getMessage());
        }
        if (earthlyBranches == null || earthlyBranches.size() != 4) {
            throw new InvalidSajuDataException(ErrorMessageConstants.EARTHLY_BRANCHES_4_INVALID.getMessage());
        }
        int officerScore = calculateOfficerScore(tenGodDistribution, hiddenStems, dayMaster);
        String monthBranch = earthlyBranches.get(SajuPillarIndex.MONTH_INDEX);

        if (FAVORABLE_OFFICER_BRANCHES_H1.contains(monthBranch)) {
            return officerScore >= 0 ? "H1" : "H2";
        }
        return officerScore >= 0 ? "H2" : "H1";
    }

    /**
     * 관성 강도 점수를 계산합니다. 양수이면 상반기, 음수이면 하반기 유리.
     * - 정관·편관: 가점 (관성 강도)
     * - 식신·상관: 감점 (관성 설기)
     * - 비겹 2개 이상: 감점 (관성 부담)
     */
    private int calculateOfficerScore(TenGodDistribution tenGodDistribution,
                                       HiddenStems hiddenStems,
                                       String dayMaster) {
        int score = 0;

        for (Map.Entry<String, Integer> entry : tenGodDistribution.asMap().entrySet()) {
            TenGodConstants tenGod = TenGodConstants.fromName(entry.getKey());
            if (tenGod != null) {
                score += entry.getValue() * tenGod.getScoreModifier();
            }
        }

        // 지장간 기반 보정: 정관/편관 +5, 식신/상관 -3
        for (List<String> stems : hiddenStems.asMap().values()) {
            for (String stem : stems) {
                String tenGodName = tenGodCalculator.getTenGod(dayMaster, stem);
                TenGodConstants tenGod = TenGodConstants.fromName(tenGodName);
                if (tenGod != null && tenGod.isOfficer()) {
                    score += 5;
                } else if (tenGod != null && WEAKENING_GODS.contains(tenGodName)) {
                    score -= 3;
                }
            }
        }

        return score;
    }

    /**
     * 신뢰도 점수(0~100)를 계산합니다.
     */
    public int calculateConfidenceScore(TenGodDistribution tenGodDistribution,
                                         HiddenStems hiddenStems,
                                         String dayMaster) {
        int officerCount = OFFICER_GODS.stream()
                .mapToInt(tenGodDistribution::getScore)
                .sum();

        int hiddenOfficerCount = 0;
        for (Map.Entry<String, List<String>> entry : hiddenStems.asMap().entrySet()) {
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

    public String buildReasoning(String favoredPeriod, TenGodDistribution tenGodDistribution) {
        if (tenGodDistribution == null || tenGodDistribution.asMap().isEmpty()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.TEN_GOD_DATA_NULL.getMessage());
        }
        if (!"H1".equals(favoredPeriod) && !"H2".equals(favoredPeriod)) {
            throw new InvalidSajuDataException(
                    "길한 시기는 H1 또는 H2만 가능합니다. 받은 값: " + favoredPeriod);
        }
        int officerCount = OFFICER_GODS.stream()
                .mapToInt(tenGodDistribution::getScore)
                .sum();
        String period = "H1".equals(favoredPeriod) ? "상반기" : "하반기";
        StringBuilder sb = new StringBuilder(period + "가 취업에 유리합니다. ");
        if (officerCount > 0) {
            sb.append("관성의 운이 ").append(period)
              .append("에 집중되어 있어 조직의 부름이 많아지고, 면접에서 호의적인 평가를 받기 쉬운 시기입니다. ");
        }
        sb.append("십신·지장간 통합 분석 기준입니다.");
        return sb.toString();
    }
}
