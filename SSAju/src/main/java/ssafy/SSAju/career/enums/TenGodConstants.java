package ssafy.SSAju.career.enums;

/**
 * 십신(十神) 상수 정의.
 *
 * 십신은 오행의 상생(相生)과 상극(相克) 관계에서 파생되는 10가지 기본 성질을 나타냅니다.
 * 일간을 기준으로 다른 천간과의 관계를 분류합니다.
 *
 * <p>십신 분류:
 * <ul>
 *   <li><b>관성(官性)</b>: 정관, 편관 - 직업, 지위, 부름의 의미</li>
 *   <li><b>재성(財性)</b>: 정재, 편재 - 금전, 이익의 의미</li>
 *   <li><b>식상(食傷)</b>: 식신, 상관 - 표현, 창의의 의미</li>
 *   <li><b>비겁(比劫)</b>: 비견, 겁재 - 동료, 경쟁의 의미</li>
 *   <li><b>인성(印性)</b>: 정인, 편인 - 지혜, 보호의 의미</li>
 * </ul>
 *
 * @author SSAju Team
 * @see ssafy.SSAju.career.util.TenGodCalculator
 * @see ssafy.SSAju.career.util.CareerFortuneAnalyzer
 */
public enum TenGodConstants {
    // === 관성 (官性) - 직업, 지위, 부름 ===
    /** 정관(正官): 나를 극하는 오행, 같은 음양 - 조직 내 정당한 권위와 지위 */
    CHIEF_OFFICER("정관", "官", 20, true),
    /** 편관(偏官/七殺): 나를 극하는 오행, 다른 음양 - 불규칙한 권력과 강력한 행동력 */
    SIDE_OFFICER("편관", "殺", 20, true),

    // === 재성 (財性) - 금전, 이익 ===
    /** 정재(正財): 내가 극하는 오행, 다른 음양 - 정당한 수익 */
    CHIEF_WEALTH("정재", "財", 0, false),
    /** 편재(偏財): 내가 극하는 오행, 같은 음양 - 예상 외의 이익 */
    SIDE_WEALTH("편재", "才", 0, false),

    // === 식상 (食傷) - 표현, 창의 ===
    /** 식신(食神): 내가 생하는 오행, 같은 음양 - 온화하고 표현적인 창의 */
    FOOD_GOD("식신", "食", -15, false),
    /** 상관(傷官): 내가 생하는 오행, 다른 음양 - 강렬하고 도전적인 표현 */
    INJURING_OFFICER("상관", "傷", -15, false),

    // === 비겁 (比劫) - 동료, 경쟁 ===
    /** 비견(比肩): 같은 오행, 같은 음양 - 협력하는 동료 */
    COMPARING_FRIEND("비견", "比", -5, false),
    /** 겁재(劫財): 같은 오행, 다른 음양 - 경쟁하는 라이벌 */
    ROBBING_WEALTH("겁재", "劫", -5, false),

    // === 인성 (印性) - 지혜, 보호 ===
    /** 정인(正印): 나를 생하는 오행, 다른 음양 - 정통 학문과 지혜 */
    CHIEF_SEAL("정인", "印", 0, false),
    /** 편인(偏印): 나를 생하는 오행, 같은 음양 - 특수 기술과 창의 */
    SIDE_SEAL("편인", "梭", 0, false);

    private final String name;          // 십신 이름 (예: "정관")
    private final String symbol;        // 십신 기호 (예: "官")
    private final int scoreModifier;    // 관운 점수 가점/감점 (20, -15, -5, 0)
    private final boolean isOfficer;    // 관성 여부

    TenGodConstants(String name, String symbol, int scoreModifier, boolean isOfficer) {
        this.name = name;
        this.symbol = symbol;
        this.scoreModifier = scoreModifier;
        this.isOfficer = isOfficer;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * 관운 분석에서 십신별 점수 수정자 반환.
     * @return 가점(양수) 또는 감점(음수). 정관/편관은 +20, 식신/상관은 -15, 비겁은 -5
     */
    public int getScoreModifier() {
        return scoreModifier;
    }

    /**
     * 이 십신이 관성(정관 또는 편관)인지 여부.
     */
    public boolean isOfficer() {
        return isOfficer;
    }

    /**
     * 십신 이름으로 상수 조회.
     * @param name 십신 이름 (예: "정관")
     * @return 매칭되는 TenGodConstants, 없으면 null
     */
    public static TenGodConstants fromName(String name) {
        for (TenGodConstants tg : TenGodConstants.values()) {
            if (tg.name.equals(name)) {
                return tg;
            }
        }
        return null;
    }
}
