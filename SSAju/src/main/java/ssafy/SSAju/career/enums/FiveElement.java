package ssafy.SSAju.career.enums;

import java.util.Arrays;
import java.util.List;
import ssafy.SSAju.exception.InvalidSajuDataException;

/**
 * 오행(五行) 열거형.
 * <p>
 * 음양오행론에서 사용하는 5가지 기운(木·火·土·金·水)과 각 오행의 상극(相剋) 관계를 정의합니다.
 * 각 오행에 대응하는 천간(天干) 목록도 포함합니다.
 */
public enum FiveElement {

    WOOD ("木", List.of("甲", "乙"),        List.of(3, 4, 5)),
    FIRE ("火", List.of("丙", "丁"),        List.of(6, 7, 8)),
    EARTH("土", List.of("戊", "己"),        List.of()),
    METAL("金", List.of("庚", "辛"),        List.of(9, 10, 11)),
    WATER("水", List.of("壬", "癸"),        List.of(12, 1, 2));

    private final String symbol;
    private final List<String> heavenlyStems;
    /** 24절기 기준 해당 계절에 속하는 월 목록. EARTH(환절기)는 빈 리스트. */
    private final List<Integer> seasonMonths;

    FiveElement(String symbol, List<String> heavenlyStems, List<Integer> seasonMonths) {
        this.symbol = symbol;
        this.heavenlyStems = heavenlyStems;
        this.seasonMonths = seasonMonths;
    }

    public String getSymbol() {
        return symbol;
    }

    public List<String> getHeavenlyStems() {
        return heavenlyStems;
    }

    /**
     * 월(1~12)로 해당 계절 오행을 조회합니다. (24절기 기준)
     * 환절기에 해당하는 월이 없으면 EARTH를 반환합니다.
     */
    public static FiveElement fromMonth(int month) {
        return Arrays.stream(values())
                .filter(e -> e.seasonMonths.contains(month))
                .findFirst()
                .orElse(EARTH);
    }

    /**
     * 천간(天干) 한자로 해당 오행을 조회합니다.
     *
     * @param stem 천간 한자 (예: "甲", "乙")
     * @return 해당 FiveElement
     * @throws InvalidSajuDataException 알 수 없는 천간 값인 경우
     */
    public static FiveElement fromStem(String stem) {
        return Arrays.stream(values())
                .filter(e -> e.heavenlyStems.contains(stem))
                .findFirst()
                .orElseThrow(() -> new InvalidSajuDataException("알 수 없는 천간 값입니다: " + stem));
    }

    /**
     * 모든 오행의 한자 기호 목록을 반환합니다.
     *
     * @return ["木", "火", "土", "金", "水"]
     */
    public static List<String> allSymbols() {
        return Arrays.stream(values())
                .map(FiveElement::getSymbol)
                .toList();
    }

    /**
     * 한자 기호로 FiveElement를 조회합니다.
     *
     * @param symbol 오행 한자 (예: "木")
     * @return 해당 FiveElement
     * @throws IllegalArgumentException 알 수 없는 오행 값인 경우
     */
    public static FiveElement fromSymbol(String symbol) {
        return Arrays.stream(values())
                .filter(e -> e.symbol.equals(symbol))
                .findFirst()
                .orElseThrow(() -> new InvalidSajuDataException("알 수 없는 오행 값입니다: " + symbol));
    }

    /**
     * 이 오행과 상극(相剋) 관계인 오행을 반환합니다.
     * <pre>
     *   木 → 金(金克木), 金 → 木(木克金)
     *   火 → 水(水克火), 水 → 火(火克水)
     *   土 → 木(木克土)
     * </pre>
     */
    public FiveElement opposing() {
        return switch (this) {
            case WOOD  -> METAL;
            case METAL -> WOOD;
            case FIRE  -> WATER;
            case WATER -> FIRE;
            case EARTH -> WOOD;
        };
    }
}
