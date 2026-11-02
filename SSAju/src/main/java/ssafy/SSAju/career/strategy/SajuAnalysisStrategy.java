package ssafy.SSAju.career.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.AnalysisTimeContext;
import ssafy.SSAju.career.domain.BirthInfo;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.provider.SajuAnalysisFacade;
import ssafy.SSAju.career.util.AnalysisConstants;
import ssafy.SSAju.career.validator.RequestValidator;
import ssafy.SSAju.dto.request.CareerTimingRequest;
import ssafy.SSAju.exception.InvalidSajuDataException;

/**
 * 관운 분석 전략.
 *
 * <p>저장된 만세력의 십성·지장간을 그대로 쓰되, 최종 해석(favoredPeriod·confidenceScore·reasoning)은
 * 요청마다 새로 계산해 결과 스냅샷에만 저장한다. 만세력에는 해석을 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SajuAnalysisStrategy
        implements AnalysisStrategy<CareerTimingRequest, AnalysisPayload.SajuResultData> {

    private final SajuAnalysisFacade sajuAnalysisFacade;
    private final RequestValidator requestValidator;

    @Override
    public boolean supports(AnalysisType type) {
        return AnalysisType.SAJU == type;
    }

    @Override
    public Class<CareerTimingRequest> inputType() {
        return CareerTimingRequest.class;
    }

    @Override
    public BirthInfo birthInfo(CareerTimingRequest input) {
        return new BirthInfo(input.birthDate(), input.birthTime());
    }

    @Override
    public void validateInput(CareerTimingRequest input) {
        requestValidator.validateBirthInfo(input.birthDate(), input.birthTime());
    }

    @Override
    public AnalysisOutcome<AnalysisPayload.SajuResultData> analyze(CareerTimingRequest input,
                                                                   ManseData manseData,
                                                                   AnalysisTimeContext time) {
        SajuAnalysisFacade.SajuAnalysisContext ctx = sajuAnalysisFacade.analyze(manseData);

        return new AnalysisOutcome<>(
                AnalysisPayload.SajuInput.of(input.birthDate(), input.birthTime(), time),
                AnalysisPayload.SajuResultData.of(
                        ctx.favoredPeriod(), ctx.confidenceScore(), ctx.reasoning()));
    }

    @Override
    public void validateResult(AnalysisPayload.SajuResultData result) {
        if (result.favoredPeriod() == null || result.favoredPeriod().isBlank()
                || result.reasoning() == null || result.reasoning().isBlank()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_RESULT_INVALID.getMessage());
        }
        if (result.confidenceScore() < AnalysisConstants.MIN_SCORE
                || result.confidenceScore() > AnalysisConstants.MAX_SCORE) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_RESULT_INVALID.getMessage());
        }
    }
}
