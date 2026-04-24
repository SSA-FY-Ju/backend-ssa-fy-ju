package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;
import ssafy.SSAju.dto.external.FastAPIResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 십신(十神) 계산기: 일간(日干)을 기준으로 각 천간의 십신을 판정한다.
 * 십신: 비겁, 식상, 재성, 관성, 인성 (각각 음양으로 세분화)
 */
@Component
public class TenGodCalculator {

    // 천간 음양 테이블: 갑/병/무/경/임 = 양(+), 을/정/기/신/계 = 음(-)
    private static final Map<String, Integer> STEM_YIN_YANG = new HashMap<>();

    static {
        STEM_YIN_YANG.put("甲", 1); STEM_YIN_YANG.put("丙", 1);
        STEM_YIN_YANG.put("戊", 1); STEM_YIN_YANG.put("庚", 1);
        STEM_YIN_YANG.put("壬", 1);
        STEM_YIN_YANG.put("乙", -1); STEM_YIN_YANG.put("丁", -1);
        STEM_YIN_YANG.put("己", -1); STEM_YIN_YANG.put("辛", -1);
        STEM_YIN_YANG.put("癸", -1);
    }

    // 천간 오행 테이블
    private static final Map<String, String> STEM_ELEMENT = new HashMap<>();

    static {
        STEM_ELEMENT.put("甲", "木"); STEM_ELEMENT.put("乙", "木");
        STEM_ELEMENT.put("丙", "火"); STEM_ELEMENT.put("丁", "火");
        STEM_ELEMENT.put("戊", "土"); STEM_ELEMENT.put("己", "土");
        STEM_ELEMENT.put("庚", "金"); STEM_ELEMENT.put("辛", "金");
        STEM_ELEMENT.put("壬", "水"); STEM_ELEMENT.put("癸", "水");
    }

    // 상생 순환 순서: 木→火→土→金→水→木 (인덱스 차이로 관계 판별)
    private static final List<String> ELEMENTS = List.of("木", "火", "土", "金", "水");

    // 12지지별 지장간(支藏干) 테이블
    private static final Map<String, List<String>> BRANCH_HIDDEN_STEMS = Map.ofEntries(
            Map.entry("子", List.of("壬", "癸")),
            Map.entry("丑", List.of("己", "癸", "辛")),
            Map.entry("寅", List.of("戊", "丙", "甲")),
            Map.entry("卯", List.of("甲", "乙")),
            Map.entry("辰", List.of("乙", "癸", "戊")),
            Map.entry("巳", List.of("戊", "庚", "丙")),
            Map.entry("午", List.of("己", "丁")),
            Map.entry("未", List.of("丁", "乙", "己")),
            Map.entry("申", List.of("戊", "壬", "庚")),
            Map.entry("酉", List.of("庚", "辛")),
            Map.entry("戌", List.of("辛", "丁", "戊")),
            Map.entry("亥", List.of("甲", "壬"))
    );

    /**
     * 일간을 기준으로 대상 천간의 십신을 계산한다.
     */
    public String calculate(String dayStem, String targetStem) {
        String dayElement = STEM_ELEMENT.get(dayStem);
        String targetElement = STEM_ELEMENT.get(targetStem);

        if (dayElement == null || targetElement == null) {
            return "UNKNOWN";
        }

        int dayPolarity = STEM_YIN_YANG.getOrDefault(dayStem, 0);
        int targetPolarity = STEM_YIN_YANG.getOrDefault(targetStem, 0);
        boolean samePolarity = dayPolarity == targetPolarity;

        return determineTenGod(dayElement, targetElement, samePolarity);
    }

    /**
     * FastAPI 응답에서 사주 전체 십신 맵을 계산한다 (천간 기준).
     */
    public Map<String, String> calculateAll(FastAPIResponse sajuData) {
        String dayStem = sajuData.dayStem();
        Map<String, String> result = new HashMap<>();
        result.put("yearStem", calculate(dayStem, sajuData.yearStem()));
        result.put("monthStem", calculate(dayStem, sajuData.monthStem()));
        result.put("hourStem", calculate(dayStem, sajuData.hourStem()));
        return result;
    }

    /**
     * 지지(地支) 하나에서 지장간(支藏干)의 십신 목록을 반환한다.
     */
    public List<String> calculateFromBranch(String dayStem, String branch) {
        List<String> hiddenStems = BRANCH_HIDDEN_STEMS.get(branch);
        if (hiddenStems == null) return List.of();
        return hiddenStems.stream()
                .map(stem -> calculate(dayStem, stem))
                .toList();
    }

    /**
     * FastAPI 응답에서 년·월·일·시 지지 4개의 지장간 십신을 모두 계산한다.
     */
    public Map<String, List<String>> calculateAllFromBranches(FastAPIResponse sajuData) {
        String dayStem = sajuData.dayStem();
        Map<String, List<String>> result = new HashMap<>();
        result.put("yearBranch", calculateFromBranch(dayStem, sajuData.yearBranch()));
        result.put("monthBranch", calculateFromBranch(dayStem, sajuData.monthBranch()));
        result.put("dayBranch", calculateFromBranch(dayStem, sajuData.dayBranch()));
        result.put("hourBranch", calculateFromBranch(dayStem, sajuData.hourBranch()));
        return result;
    }

    private String determineTenGod(String dayElement, String targetElement, boolean samePolarity) {
        return switch (getRelation(dayElement, targetElement)) {
            case "SAME"     -> samePolarity ? "비견(比肩)" : "겁재(劫財)";
            case "PRODUCE"  -> samePolarity ? "식신(食神)" : "상관(傷官)";
            case "CONTROL"  -> samePolarity ? "편재(偏財)" : "정재(正財)";
            case "CONTROLLED_BY" -> samePolarity ? "편관(偏官)" : "정관(正官)";
            case "PRODUCED_BY"  -> samePolarity ? "편인(偏印)" : "정인(正印)";
            default -> "UNKNOWN";
        };
    }

    private String getRelation(String day, String target) {
        int dayIdx = ELEMENTS.indexOf(day);
        int targetIdx = ELEMENTS.indexOf(target);
        if (dayIdx == -1 || targetIdx == -1) return "UNKNOWN";

        return switch ((targetIdx - dayIdx + 5) % 5) {
            case 0 -> "SAME";
            case 1 -> "PRODUCE";
            case 2 -> "CONTROL";
            case 3 -> "CONTROLLED_BY";
            case 4 -> "PRODUCED_BY";
            default -> "UNKNOWN";
        };
    }
}
