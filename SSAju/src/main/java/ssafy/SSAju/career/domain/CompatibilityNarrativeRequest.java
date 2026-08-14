package ssafy.SSAju.career.domain;

import ssafy.SSAju.career.util.JobCategoryEnum;

/**
 * {@code CompanyMatchingOpenAICaller}에 전달되는 궁합 해설 생성 요청 VO.
 *
 * <p>이미 계산된 점수(궁합/직군매칭/역할별)와 사주 데이터를 묶어 전달하며,
 * AI는 이 점수를 재계산하지 않고 해설 텍스트만 생성한다.
 */
public record CompatibilityNarrativeRequest(
        FiveElements userFiveElements,
        HiddenStems userHiddenStems,
        String userDayMaster,
        FiveElements companyFiveElements,
        HiddenStems companyHiddenStems,
        String companyDayMaster,
        int compatibilityScore,
        int matchScore,
        int primaryScore,
        int secondaryScore,
        JobCategoryEnum category,
        String detailName
) {}
