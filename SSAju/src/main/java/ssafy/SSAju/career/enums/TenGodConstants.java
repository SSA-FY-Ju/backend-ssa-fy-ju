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
    CHIEF_OFFICER("정관", "官", 20, true,
            "책임감과 원칙을 중시하며 체계적인 조직에서 두각을 나타냅니다. " +
            "신뢰를 바탕으로 조직 내 안정적인 지위를 쌓아가는 유형입니다."),

    /** 편관(偏官/七殺): 나를 극하는 오행, 다른 음양 - 불규칙한 권력과 강력한 행동력 */
    SIDE_OFFICER("편관", "殺", 20, true,
            "강한 추진력과 도전 정신을 갖추고 있으며 목표 달성을 위해 과감하게 행동합니다. " +
            "경쟁 환경에서 특히 두각을 나타냅니다."),

    // === 재성 (財性) - 금전, 이익 ===
    /** 정재(正財): 내가 극하는 오행, 다른 음양 - 정당한 수익 */
    CHIEF_WEALTH("정재", "財", 0, false,
            "성실하고 꼼꼼한 노력으로 안정적인 수익을 추구합니다. " +
            "계획적인 재무 감각으로 착실하게 성과를 축적하는 성향입니다."),

    /** 편재(偏財): 내가 극하는 오행, 같은 음양 - 예상 외의 이익 */
    SIDE_WEALTH("편재", "才", 0, false,
            "폭넓은 대인관계를 활용해 예상치 못한 기회를 포착하는 능력이 있습니다. " +
            "영업·사업·투자 분야에서 강점을 발휘합니다."),

    // === 식상 (食傷) - 표현, 창의 ===
    /** 식신(食神): 내가 생하는 오행, 같은 음양 - 온화하고 표현적인 창의 */
    FOOD_GOD("식신", "食", -15, false,
            "온화한 창의성과 여유로운 표현력을 지닙니다. " +
            "아이디어를 구체화하는 능력이 뛰어나며 안정적인 생산성을 발휘합니다."),

    /** 상관(傷官): 내가 생하는 오행, 다른 음양 - 강렬하고 도전적인 표현 */
    INJURING_OFFICER("상관", "傷", -15, false,
            "기존 틀을 깨는 독창적 사고력과 강렬한 표현욕을 가지고 있습니다. " +
            "혁신적인 아이디어로 변화를 주도하는 유형입니다."),

    // === 비겁 (比劫) - 동료, 경쟁 ===
    /** 비견(比肩): 같은 오행, 같은 음양 - 협력하는 동료 */
    COMPARING_FRIEND("비견", "比", -5, false,
            "강한 독립심과 자존감을 지닌 유형입니다. " +
            "동료와 경쟁하면서도 자신만의 방식을 고수하는 경향이 있습니다."),

    /** 겁재(劫財): 같은 오행, 다른 음양 - 경쟁하는 라이벌 */
    ROBBING_WEALTH("겁재", "劫", -5, false,
            "강한 승부욕과 결단력으로 자원을 과감하게 활용합니다. " +
            "도전적인 상황에서 빠른 판단력을 발휘합니다."),

    // === 인성 (印性) - 지혜, 보호 ===
    /** 정인(正印): 나를 생하는 오행, 다른 음양 - 정통 학문과 지혜 */
    CHIEF_SEAL("정인", "印", 0, false,
            "깊은 학문적 소양과 체계적인 사고력을 갖추고 있습니다. " +
            "꾸준한 학습과 안정적인 환경에서 성장하는 유형입니다."),

    /** 편인(偏印): 나를 생하는 오행, 같은 음양 - 특수 기술과 창의 */
    SIDE_SEAL("편인", "梭", 0, false,
            "남다른 직관력과 특수 분야의 전문성을 지닙니다. " +
            "일반적이지 않은 방식으로 독보적인 역량을 발휘합니다.");

    private final String name;              // 십신 이름 (예: "정관")
    private final String symbol;            // 십신 기호 (예: "官")
    private final int scoreModifier;        // 관운 점수 가점/감점 (20, -15, -5, 0)
    private final boolean isOfficer;        // 관성 여부
    private final String characteristics;  // 십신 특성 문구

    TenGodConstants(String name, String symbol, int scoreModifier, boolean isOfficer,
                    String characteristics) {
        this.name = name;
        this.symbol = symbol;
        this.scoreModifier = scoreModifier;
        this.isOfficer = isOfficer;
        this.characteristics = characteristics;
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
     * 십신의 커리어 특성 문구를 반환합니다.
     * AI 프롬프트 및 사주 분석 결과 설명에 활용됩니다.
     */
    public String getCharacteristics() {
        return characteristics;
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
