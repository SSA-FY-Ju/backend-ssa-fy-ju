package ssafy.SSAju.career.domain;

import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.InvalidSajuDataException;

/**
 * 커밋이 끝난 분석 한 건 — 발급된 숫자 ID와 저장된 완성 결과.
 *
 * <p>결과 스냅샷 자체에는 ID가 없으므로, 응답을 만들 때 이 record의 ID를 붙인다.
 */
public record AnalysisExecution(Long analysisId, AnalysisPayload.Result result) {
    public AnalysisExecution {
        if (analysisId == null || result == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_SNAPSHOT_REQUIRED.getMessage());
        }
    }
}
