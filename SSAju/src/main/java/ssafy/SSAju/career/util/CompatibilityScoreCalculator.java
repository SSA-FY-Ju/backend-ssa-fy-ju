package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 사주 궁합 점수 계산기
 * 사용자 사주와 기업 설립일 사주를 비교하여 궁합 점수(0~100)를 계산합니다.
 *
 * 점수 산정 기준:
 * - 오행 균형: 사용자 오행의 부족한 부분을 기업 사주가 보완하는 정도
 * - 십신 조화: 사용자의 관성(정관/편관)이 기업 사주에 잘 나타나는 정도
 * - 지장간 보완: 지장간을 포함한 오행 분포 비교
 */
@Component
public class CompatibilityScoreCalculator {

    private static final Map<String, String> ELEMENT_MAP = Map.of(
            "甲", "木", "乙", "木",
            "丙", "火", "丁", "火",
            "戊", "土", "己", "土",
            "庚", "金", "辛", "金",
            "壬", "水", "癸", "水"
    );

    private static final List<String> ALL_ELEMENTS = List.of("木", "火", "土", "金", "水");

    private final TenGodCalculator tenGodCalculator;

    public CompatibilityScoreCalculator(TenGodCalculator tenGodCalculator) {
        this.tenGodCalculator = tenGodCalculator;
    }

    /**
     * 궁합 점수를 계산합니다.
     *
     * @param userHiddenStems 사용자 지장간
     * @param userDayMaster 사용자 일간
     * @param companyHiddenStems 기업 지장간
     * @param companyDayMaster 기업 설립일 일간
     * @return 궁합 점수 (0~100)
     */
    public int calculate(Map<String, List<String>> userHiddenStems,
                         String userDayMaster,
                         Map<String, List<String>> companyHiddenStems,
                         String companyDayMaster) {

        int officerHarmonyScore = calculateOfficerHarmony(userHiddenStems,
                userDayMaster, companyDayMaster);
        int elementBalanceScore = calculateElementBalance(userHiddenStems, companyHiddenStems);

        // 가중 평균: 관성 조화 60%, 오행 균형 40%
        int rawScore = (int) (officerHarmonyScore * 0.6 + elementBalanceScore * 0.4);
        return Math.max(0, Math.min(rawScore, 100));
    }

    private int calculateOfficerHarmony(Map<String, List<String>> userHiddenStems,
                                         String userDayMaster,
                                         String companyDayMaster) {
        // 기업 일간이 사용자 일간의 관성(정관/편관)에 해당하는지 확인
        String tenGodOfCompany = tenGodCalculator.getTenGod(userDayMaster, companyDayMaster);
        boolean isOfficer = tenGodOfCompany.equals("정관") || tenGodOfCompany.equals("편관");

        int baseScore = isOfficer ? 70 : 40;

        // 지장간 내 사용자 관성 강도 보정
        int userOfficerCount = 0;
        for (List<String> stems : userHiddenStems.values()) {
            for (String stem : stems) {
                String god = tenGodCalculator.getTenGod(userDayMaster, stem);
                if (god.equals("정관") || god.equals("편관")) {
                    userOfficerCount++;
                }
            }
        }

        return Math.min(baseScore + userOfficerCount * 5, 100);
    }

    private int calculateElementBalance(Map<String, List<String>> userHiddenStems,
                                         Map<String, List<String>> companyHiddenStems) {
        Map<String, Integer> userElements = countElements(userHiddenStems);
        Map<String, Integer> companyElements = countElements(companyHiddenStems);

        int complementScore = 0;
        for (String element : ALL_ELEMENTS) {
            int userCount = userElements.getOrDefault(element, 0);
            int companyCount = companyElements.getOrDefault(element, 0);
            // 사용자에게 부족한 오행을 기업이 가지고 있으면 높은 점수
            if (userCount == 0 && companyCount > 0) {
                complementScore += 20;
            } else if (userCount > 0 && companyCount > 0) {
                complementScore += 10;
            }
        }
        return Math.min(complementScore, 100);
    }

    private Map<String, Integer> countElements(Map<String, List<String>> hiddenStems) {
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (List<String> stems : hiddenStems.values()) {
            for (String stem : stems) {
                String element = ELEMENT_MAP.get(stem);
                if (element != null) {
                    counts.merge(element, 1, Integer::sum);
                }
            }
        }
        return counts;
    }
}
