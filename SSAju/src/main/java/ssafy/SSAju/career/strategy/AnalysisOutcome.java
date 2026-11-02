package ssafy.SSAju.career.strategy;

import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.InvalidSajuDataException;

/**
 * 전략이 한 번의 분석으로 만들어낸 저장 대상 — 확정된 입력 스냅샷과 완성 결과 스냅샷.
 *
 * <p>입력도 함께 돌려주는 이유는 요청값만으로 입력 스냅샷을 만들 수 없기 때문이다. 궁합은 분석
 * 도중에야 실제 사용한 회사 설립일·취득 경로가 정해진다.
 *
 * <p>분석 ID는 담지 않는다 — 아직 저장 전이며, ID는 커밋 후 컬럼에서 주입한다.
 */
public record AnalysisOutcome<R extends AnalysisPayload.Result>(
        AnalysisPayload.Input input,
        R result
) {
    public AnalysisOutcome {
        if (input == null || result == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_SNAPSHOT_REQUIRED.getMessage());
        }
    }
}
