package ssafy.SSAju.career.strategy;

import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.AnalysisTimeContext;
import ssafy.SSAju.career.domain.BirthInfo;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.enums.AnalysisType;

/**
 * 분석 유형 하나를 수행하는 전략.
 *
 * <p>공통 실행 서비스는 구현체 목록을 주입받아 {@link #supports(AnalysisType)}로 고른다. 전략은
 * "이 유형의 분석을 어떻게 계산하는가"만 책임지고, 인증·쿼터 차감·저장·보상은 공통 서비스가 맡는다.
 *
 * <p>입력 타입 확인과 cast는 유형 선택 경계에서 {@link #inputType()}으로 한 번만 수행한다.
 *
 * @param <I> 유형별 요청 DTO
 * @param <R> 유형별 결과 스냅샷 record
 */
public interface AnalysisStrategy<I, R extends AnalysisPayload.Result> {

    /** 이 전략이 처리하는 분석 유형인지 판단한다. */
    boolean supports(AnalysisType type);

    /** 공통 서비스가 요청 객체를 안전하게 cast하기 위한 입력 타입. */
    Class<I> inputType();

    /**
     * 분석에 사용할 출생정보를 요청에서 정규화해 돌려준다. 공통 서비스는 이 값으로 프로필·만세력을
     * 확보한 뒤 {@link #analyze}를 호출한다.
     */
    BirthInfo birthInfo(I input);

    /** 유형별 요청 검증. 기존 검증기를 그대로 사용한다. */
    void validateInput(I input);

    /**
     * 저장된 만세력과 확정된 시간 문맥으로 이번 요청의 분석을 수행한다.
     *
     * <p>만세력을 재사용해도 유형별 계산과 외부 응답 검증은 매번 수행한다. 시간 문맥은 요청 시작
     * 시점에 확정된 값이며, 전략과 그 아래 caller·조립 단계는 현재 시각을 다시 읽지 않는다.
     */
    AnalysisOutcome<R> analyze(I input, ManseData manseData, AnalysisTimeContext time);

    /** 저장 직전 완성 결과 검증. 통과하지 못한 결과는 저장하지 않는다. */
    void validateResult(R result);
}
