package ssafy.SSAju.career.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.enums.FiveElement;
import ssafy.SSAju.career.enums.TenGodConstants;
import ssafy.SSAju.career.validator.CompatibilityValidator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
@RequiredArgsConstructor
public class CompatibilityScoreCalculator {

    private final TenGodCalculator tenGodCalculator;
    private final CompatibilityValidator compatibilityValidator;

    /**
     * 궁합 점수를 계산합니다.
     *
     * @param userHiddenStems 사용자 지장간
     * @param userDayMaster 사용자 일간
     * @param companyHiddenStems 기업 지장간
     * @param companyDayMaster 기업 설립일 일간
     * @return 궁합 점수 (0~100)
     */
    public int calculate(HiddenStems userHiddenStems,
                         String userDayMaster,
                         HiddenStems companyHiddenStems,
                         String companyDayMaster) {
        compatibilityValidator.validate(userHiddenStems, userDayMaster, companyHiddenStems, companyDayMaster);

        int officerHarmonyScore = calculateOfficerHarmony(userHiddenStems,
                userDayMaster, companyDayMaster);
        int elementBalanceScore = calculateElementBalance(userHiddenStems, companyHiddenStems);

        // 가중 평균: 관성 조화 60%, 오행 균형 40%
        int rawScore = (int) (officerHarmonyScore * 0.6 + elementBalanceScore * 0.4);
        return Math.max(0, Math.min(rawScore, 100));
    }

    private int calculateOfficerHarmony(HiddenStems userHiddenStems,
                                         String userDayMaster,
                                         String companyDayMaster) {
        // 기업 일간이 사용자 일간의 관성(정관/편관)에 해당하는지 확인
        String tenGodOfCompany = tenGodCalculator.getTenGod(userDayMaster, companyDayMaster);
        TenGodConstants tenGod = TenGodConstants.fromName(tenGodOfCompany);
        boolean isOfficer = tenGod != null && tenGod.isOfficer();

        int baseScore = isOfficer ? 70 : 40;

        // 지장간 내 사용자 관성 강도 보정
        int userOfficerCount = 0;
        for (List<String> stems : userHiddenStems.asMap().values()) {
            for (String stem : stems) {
                String godName = tenGodCalculator.getTenGod(userDayMaster, stem);
                TenGodConstants god = TenGodConstants.fromName(godName);
                if (god != null && god.isOfficer()) {
                    userOfficerCount++;
                }
            }
        }

        return Math.min(baseScore + userOfficerCount * 5, 100);
    }

    private int calculateElementBalance(HiddenStems userHiddenStems,
                                         HiddenStems companyHiddenStems) {
        Map<String, Integer> userElements = countElements(userHiddenStems);
        Map<String, Integer> companyElements = countElements(companyHiddenStems);

        int complementScore = 0;
        for (String element : FiveElement.allSymbols()) {
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

    private Map<String, Integer> countElements(HiddenStems hiddenStems) {
        Map<String, Integer> counts = new HashMap<>();
        for (List<String> stems : hiddenStems.asMap().values()) {
            for (String stem : stems) {
                Optional.ofNullable(stem)
                        .map(s -> {
                            try { return FiveElement.fromStem(s); }
                            catch (Exception e) { return null; }
                        })
                        .ifPresent(fe -> counts.merge(fe.getSymbol(), 1, Integer::sum));
            }
        }
        return counts;
    }
}
