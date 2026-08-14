package ssafy.SSAju.career.domain;

import ssafy.SSAju.career.util.JobCategoryEnum;

/**
 * {@code CompanyMatchingOpenAICaller}에 전달되는 궁합 해설 생성 요청 VO.
 *
 * <p>이미 계산된 점수(궁합/직군매칭/역할별)와 사주 데이터를 묶어 전달하며,
 * AI는 이 점수를 재계산하지 않고 해설 텍스트만 생성한다.
 *
 * <p>사용자/기업 사주 데이터를 각각 {@link SajuInfo}로, 점수 4종을 {@link ScoreSet}으로
 * 묶어 타입으로 구분함으로써, 같은 타입(int/FiveElements 등)의 인자가 나란히 놓여
 * 생성 시점에 순서가 뒤바뀌는 실수를 컴파일 단계에서 방지한다(코드리뷰 finding).
 */
public record CompatibilityNarrativeRequest(
        SajuInfo user,
        SajuInfo company,
        ScoreSet scores,
        JobCategoryEnum category,
        String detailName
) {

    /** 사주 데이터 묶음(오행 분포, 지장간, 일간). */
    public record SajuInfo(FiveElements fiveElements, HiddenStems hiddenStems, String dayMaster) {}

    /** 이미 계산 완료된 점수 4종(궁합/직군매칭/역할별 전문가·리드). */
    public record ScoreSet(int compatibilityScore, int matchScore, int primaryScore, int secondaryScore) {}
}
