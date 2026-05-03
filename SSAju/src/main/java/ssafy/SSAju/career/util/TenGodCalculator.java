package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.enums.TenGodConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 십신(十神) 계산기
 * 일간(日干)을 기준으로 나머지 천간들의 십신을 계산합니다.
 *
 * 오행: 木(甲乙), 火(丙丁), 土(戊己), 金(庚辛), 水(壬癸)
 * 음양: 陽(甲丙戊庚壬), 陰(乙丁己辛癸)
 * 십신 규칙:
 *   - 같은 오행, 같은 음양 → 비견(比肩)
 *   - 같은 오행, 다른 음양 → 겁재(劫財)
 *   - 내가 생하는 오행, 같은 음양 → 식신(食神)
 *   - 내가 생하는 오행, 다른 음양 → 상관(傷官)
 *   - 내가 극하는 오행, 같은 음양 → 편재(偏財)
 *   - 내가 극하는 오행, 다른 음양 → 정재(正財)
 *   - 나를 극하는 오행, 같은 음양 → 편관(偏官/七殺)
 *   - 나를 극하는 오행, 다른 음양 → 정관(正官)
 *   - 나를 생하는 오행, 같은 음양 → 편인(偏印)
 *   - 나를 생하는 오행, 다른 음양 → 정인(正印)
 */
@Component
public class TenGodCalculator {

    private static final Map<String, String> FIVE_ELEMENT_MAP = Map.of(
            "甲", "木", "乙", "木",
            "丙", "火", "丁", "火",
            "戊", "土", "己", "土",
            "庚", "金", "辛", "金",
            "壬", "水", "癸", "水"
    );

    private static final Map<String, String> YANG_YIN_MAP = Map.of(
            "甲", "陽", "乙", "陰",
            "丙", "陽", "丁", "陰",
            "戊", "陽", "己", "陰",
            "庚", "陽", "辛", "陰",
            "壬", "陽", "癸", "陰"
    );

    // 오행 상생(相生): 木生火, 火生土, 土生金, 金生水, 水生木
    private static final Map<String, String> GENERATES_MAP = Map.of(
            "木", "火", "火", "土",
            "土", "金", "金", "水", "水", "木"
    );

    // 오행 상극(相克): 木克土, 火克金, 土克水, 金克木, 水克火
    private static final Map<String, String> CONTROLS_MAP = Map.of(
            "木", "土", "火", "金",
            "土", "水", "金", "木", "水", "火"
    );

    public Map<String, Integer> calculate(List<String> heavenlyStems) {
        if (heavenlyStems == null || heavenlyStems.size() != 4) {
            throw new IllegalArgumentException(ErrorMessageConstants.HEAVENLY_STEMS_COUNT_INVALID.getMessage());
        }
        String dayMaster = heavenlyStems.get(2); // 일간(日干) - 인덱스 2 (年月日時 순서)
        Map<String, Integer> distribution = new HashMap<>();

        for (int i = 0; i < heavenlyStems.size(); i++) {
            if (i == 2) continue; // 일간 제외
            String stem = heavenlyStems.get(i);
            String tenGod = getTenGod(dayMaster, stem);
            distribution.merge(tenGod, 1, Integer::sum);
        }
        return distribution;
    }

    public String getTenGod(String dayMaster, String targetStem) {
        if (dayMaster == null || !FIVE_ELEMENT_MAP.containsKey(dayMaster)) {
            throw new IllegalArgumentException(ErrorMessageConstants.INVALID_DAY_MASTER.getMessage() + ": " + dayMaster);
        }
        if (targetStem == null || !FIVE_ELEMENT_MAP.containsKey(targetStem)) {
            throw new IllegalArgumentException(ErrorMessageConstants.INVALID_HEAVENLY_STEM.getMessage() + ": " + targetStem);
        }
        String masterElement = FIVE_ELEMENT_MAP.get(dayMaster);
        String targetElement = FIVE_ELEMENT_MAP.get(targetStem);
        String masterPolarity = YANG_YIN_MAP.get(dayMaster);
        String targetPolarity = YANG_YIN_MAP.get(targetStem);

        boolean samePolarity = masterPolarity.equals(targetPolarity);

        if (masterElement.equals(targetElement)) {
            return samePolarity ? TenGodConstants.COMPARING_FRIEND.getName() : TenGodConstants.ROBBING_WEALTH.getName();
        }
        if (GENERATES_MAP.get(masterElement).equals(targetElement)) {
            return samePolarity ? TenGodConstants.FOOD_GOD.getName() : TenGodConstants.INJURING_OFFICER.getName();
        }
        if (CONTROLS_MAP.get(masterElement).equals(targetElement)) {
            return samePolarity ? TenGodConstants.SIDE_WEALTH.getName() : TenGodConstants.CHIEF_WEALTH.getName();
        }
        // 나를 극하는 오행: targetElement가 masterElement를 극함
        if (CONTROLS_MAP.get(targetElement).equals(masterElement)) {
            return samePolarity ? TenGodConstants.SIDE_OFFICER.getName() : TenGodConstants.CHIEF_OFFICER.getName();
        }
        // 나를 생하는 오행: targetElement가 masterElement를 생함
        if (GENERATES_MAP.get(targetElement).equals(masterElement)) {
            return samePolarity ? TenGodConstants.SIDE_SEAL.getName() : TenGodConstants.CHIEF_SEAL.getName();
        }

        throw new IllegalArgumentException(ErrorMessageConstants.UNKNOWN_STEM_COMBINATION.getMessage() + ": " + dayMaster + " vs " + targetStem);
    }
}
