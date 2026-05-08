package ssafy.SSAju.career.enums;

/**
 * 사주(四柱) 기둥 인덱스 상수.
 *
 * 天干(천간)과 地支(지지) 목록은 年月日時 순서로 저장됩니다.
 * 인덱스를 직접 사용하는 대신 이 상수를 사용하여 의미를 명확히 합니다.
 */
public final class SajuPillarIndex {

    /** 연주(年柱) 인덱스 — 년도 천간/지지 위치. */
    public static final int YEAR_INDEX  = 0;

    /** 월주(月柱) 인덱스 — 월 천간/지지 위치. */
    public static final int MONTH_INDEX = 1;

    /** 일주(日柱) 인덱스 — 일간(日干) 위치. */
    public static final int DAY_INDEX   = 2;

    /** 시주(時柱) 인덱스 — 시 천간/지지 위치. */
    public static final int HOUR_INDEX  = 3;

    /** 사주 기둥 수 (年·月·日·時). */
    public static final int PILLAR_COUNT = 4;

    private SajuPillarIndex() {}
}
