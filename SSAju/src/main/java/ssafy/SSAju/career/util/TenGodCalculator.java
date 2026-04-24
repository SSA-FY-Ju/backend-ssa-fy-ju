package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;
import ssafy.SSAju.dto.external.FastAPIResponse;

import java.util.HashMap;
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
     * FastAPI 응답에서 사주 전체 십신 맵을 계산한다.
     */
    public Map<String, String> calculateAll(FastAPIResponse sajuData) {
        String dayStem = sajuData.dayStem();
        Map<String, String> result = new HashMap<>();
        result.put("yearStem", calculate(dayStem, sajuData.yearStem()));
        result.put("monthStem", calculate(dayStem, sajuData.monthStem()));
        result.put("hourStem", calculate(dayStem, sajuData.hourStem()));
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
        if (day.equals(target)) return "SAME";
        return switch (day) {
            case "木" -> switch (target) {
                case "火" -> "PRODUCE"; case "土" -> "CONTROL";
                case "金" -> "CONTROLLED_BY"; case "水" -> "PRODUCED_BY"; default -> "UNKNOWN";
            };
            case "火" -> switch (target) {
                case "土" -> "PRODUCE"; case "金" -> "CONTROL";
                case "水" -> "CONTROLLED_BY"; case "木" -> "PRODUCED_BY"; default -> "UNKNOWN";
            };
            case "土" -> switch (target) {
                case "金" -> "PRODUCE"; case "水" -> "CONTROL";
                case "木" -> "CONTROLLED_BY"; case "火" -> "PRODUCED_BY"; default -> "UNKNOWN";
            };
            case "金" -> switch (target) {
                case "水" -> "PRODUCE"; case "木" -> "CONTROL";
                case "火" -> "CONTROLLED_BY"; case "土" -> "PRODUCED_BY"; default -> "UNKNOWN";
            };
            case "水" -> switch (target) {
                case "木" -> "PRODUCE"; case "火" -> "CONTROL";
                case "土" -> "CONTROLLED_BY"; case "金" -> "PRODUCED_BY"; default -> "UNKNOWN";
            };
            default -> "UNKNOWN";
        };
    }
}
