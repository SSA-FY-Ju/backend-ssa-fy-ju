package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.enums.ErrorMessageConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 지장간(地藏干) 계산기
 * 각 지지(地支)에 내포된 천간(天干)들을 반환합니다.
 * 지지별 지장간 매핑 (명리학 표준 기준)
 */
@Component
public class HiddenStemCalculator {

    // 지지 → 지장간 (순서: 여기(餘氣) → 중기(中氣) → 정기(正氣))
    private static final Map<String, List<String>> HIDDEN_STEMS_TABLE = Map.ofEntries(
            Map.entry("子", List.of("癸")),
            Map.entry("丑", List.of("癸", "辛", "己")),
            Map.entry("寅", List.of("甲", "丙", "戊")),
            Map.entry("卯", List.of("乙")),
            Map.entry("辰", List.of("乙", "癸", "戊")),
            Map.entry("巳", List.of("庚", "戊", "丙")),
            Map.entry("午", List.of("丁", "己")),
            Map.entry("未", List.of("乙", "丁", "己")),
            Map.entry("申", List.of("戊", "壬", "庚")),
            Map.entry("酉", List.of("辛")),
            Map.entry("戌", List.of("丁", "辛", "戊")),
            Map.entry("亥", List.of("甲", "壬"))
    );

    /**
     * 지지 목록에서 각 지지의 지장간을 계산합니다.
     *
     * @param earthlyBranches 지지 목록 (年月日時 순서, 4개)
     * @return 지지 → 지장간 목록 Map
     */
    public Map<String, List<String>> calculate(List<String> earthlyBranches) {
        if (earthlyBranches == null || earthlyBranches.size() != 4) {
            throw new IllegalArgumentException(ErrorMessageConstants.EARTHLY_BRANCHES_COUNT_INVALID.getMessage());
        }
        Map<String, List<String>> hiddenStems = new HashMap<>();
        for (String branch : earthlyBranches) {
            List<String> stems = HIDDEN_STEMS_TABLE.get(branch);
            if (stems == null) {
                throw new IllegalArgumentException(ErrorMessageConstants.UNKNOWN_EARTHLY_BRANCH.getMessage() + ": " + branch);
            }
            hiddenStems.put(branch, stems);
        }
        return hiddenStems;
    }

    public List<String> getHiddenStems(String earthlyBranch) {
        List<String> stems = HIDDEN_STEMS_TABLE.get(earthlyBranch);
        if (stems == null) {
            throw new IllegalArgumentException(ErrorMessageConstants.UNKNOWN_EARTHLY_BRANCH.getMessage() + ": " + earthlyBranch);
        }
        return stems;
    }
}
