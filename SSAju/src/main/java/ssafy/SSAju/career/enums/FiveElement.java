package ssafy.SSAju.career.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 오행(五行) 열거형.
 * <p>
 * 음양오행론에서 사용하는 5가지 기운(木·火·土·金·水)과 각 오행의 상극(相剋) 관계를 정의합니다.
 */
public enum FiveElement {

    WOOD("木"),
    FIRE("火"),
    EARTH("土"),
    METAL("金"),
    WATER("水");

    private final String symbol;

    FiveElement(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
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
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 오행 값입니다: " + symbol));
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
