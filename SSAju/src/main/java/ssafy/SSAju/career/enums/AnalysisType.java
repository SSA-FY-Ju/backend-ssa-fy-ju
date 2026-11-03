package ssafy.SSAju.career.enums;

/**
 * 분석 유형. {@code saju.analysis_type}에 VARCHAR로 저장되며 관리자 필터·통계의 기준이기도 하다.
 */
public enum AnalysisType {

    /** 관운 분석. 외부 과금 호출이 없어 일일 쿼터를 차감하지 않는다. */
    SAJU(false),

    /** AI 커리어 컨설팅. 궁합과 하루 3회를 공유한다. */
    CAREER_CONSULTATION(true),

    /** 기업/직무 궁합. 컨설팅과 하루 3회를 공유한다. */
    COMPANY_COMPATIBILITY(true);

    private final boolean consumesDailyQuota;

    AnalysisType(boolean consumesDailyQuota) {
        this.consumesDailyQuota = consumesDailyQuota;
    }

    public boolean consumesDailyQuota() {
        return consumesDailyQuota;
    }
}
