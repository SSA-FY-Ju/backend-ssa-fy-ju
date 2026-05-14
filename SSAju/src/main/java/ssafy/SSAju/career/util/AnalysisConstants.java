package ssafy.SSAju.career.util;

/**
 * 궁합 분석 전반에서 사용되는 상수 모음.
 * <p>
 * 매직 넘버를 제거하고 의미 있는 이름으로 관리하기 위해 정의합니다.
 */
public final class AnalysisConstants {

    private AnalysisConstants() {
        // 인스턴스 생성 방지
    }

    // ─────────────────────────────────────────
    // 점수 범위
    // ─────────────────────────────────────────

    /** 점수 최대값 */
    public static final int MAX_SCORE = 100;

    /** 점수 최소값 */
    public static final int MIN_SCORE = 0;

    // ─────────────────────────────────────────
    // 분석 분류 임계값
    // ─────────────────────────────────────────

    /** 이 점수 이상이면 "상생(高궁합)" */
    public static final int HIGH_COMPATIBILITY_THRESHOLD = 75;

    /** 이 점수 이상이면 "균형(中궁합)" */
    public static final int MEDIUM_COMPATIBILITY_THRESHOLD = 50;

    // ─────────────────────────────────────────
    // AnalysisBreakdown 파생 점수 조정값
    // ─────────────────────────────────────────

    /** 성향 일치도(characterMatch) 산출 시 총점에 더하는 보정값 */
    public static final int CHARACTER_MATCH_ADJUSTMENT = 5;

    /** 시너지 잠재력(potentialSynergy) 산출 시 총점에서 빼는 보정값 */
    public static final int POTENTIAL_SYNERGY_ADJUSTMENT = 5;

    // ─────────────────────────────────────────
    // JobRoleAnalyzer 점수 가중치
    // ─────────────────────────────────────────

    /** 주 오행(primaryElement) 1개당 점수 */
    public static final int PRIMARY_MATCH_WEIGHT = 40;

    /** 부 오행(secondaryElement) 1개당 점수 */
    public static final int SECONDARY_MATCH_WEIGHT = 20;

    // ─────────────────────────────────────────
    // RoleCompatibility 점수 산출
    // ─────────────────────────────────────────

    /** 주 역할(primary) 점수 = 주오행 수 × 이 값 + BASE_OFFSET */
    public static final int PRIMARY_ROLE_SCORE_MULTIPLIER = 30;

    /** 주 역할 점수 기본 오프셋 */
    public static final int PRIMARY_ROLE_SCORE_BASE = 40;

    /** 보조 역할(secondary) 점수 페널티 */
    public static final int SECONDARY_ROLE_SCORE_PENALTY = 15;

    /** 강력 추천 태그 임계값 */
    public static final int TAG_STRONG_RECOMMEND_THRESHOLD = 75;

    /** 보통 태그 임계값 */
    public static final int TAG_NORMAL_THRESHOLD = 60;

    // ─────────────────────────────────────────
    // 상극 강도 임계값
    // ─────────────────────────────────────────

    /** 이 개수 이상이면 "강한 상극" 경고 문구를 출력 */
    public static final int STRONG_OPPOSING_THRESHOLD = 2;

    // ─────────────────────────────────────────
    // 월별 운세(ForecastScoreCalculator)
    // ─────────────────────────────────────────

    /** 월별 운세 출력 개월 수 */
    public static final int FORECAST_MONTH_COUNT = 5;

    /** 오행 보유 수 1개당 점수 증분 */
    public static final int FORECAST_SCORE_STEP = 10;

    // ─────────────────────────────────────────
    // 실행 전략(AnalysisResponseBuilder)
    // ─────────────────────────────────────────

    /** 행운의 날 첫 번째 오프셋(일) */
    public static final int LUCKY_DAY_FIRST_OFFSET  = 7;

    /** 행운의 날 두 번째 오프셋(일) */
    public static final int LUCKY_DAY_SECOND_OFFSET = 14;

    /** 행운의 날 세 번째 오프셋(일) */
    public static final int LUCKY_DAY_THIRD_OFFSET  = 21;

    /** 전략 집중 시간 문구 */
    public static final String PREFERRED_TIME = "오전 09:00 ~ 11:00";
}
