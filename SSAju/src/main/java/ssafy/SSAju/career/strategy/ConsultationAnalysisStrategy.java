package ssafy.SSAju.career.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.caller.ConsultationOpenAICaller;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.AnalysisTimeContext;
import ssafy.SSAju.career.domain.BirthInfo;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.mapper.ConsultationMapper;
import ssafy.SSAju.career.provider.SajuAnalysisFacade;
import ssafy.SSAju.career.util.AnalysisConstants;
import ssafy.SSAju.career.validator.RequestValidator;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.request.ConsultationRequest;
import ssafy.SSAju.exception.InvalidSajuDataException;

/**
 * 커리어 컨설팅 전략.
 *
 * <p>같은 달·같은 모델이어도 캐시를 재사용하지 않고 매 요청마다 새로 분석한다. 컨설팅 계산을
 * 위해 별도의 관운(SAJU) 결과 행을 만들지도 않는다 — 관운 해석은 이 결과 스냅샷 안에 함께 담긴다.
 */
@Component
@RequiredArgsConstructor
public class ConsultationAnalysisStrategy
        implements AnalysisStrategy<ConsultationRequest, AnalysisPayload.ConsultationResultData> {

    private final SajuAnalysisFacade sajuAnalysisFacade;
    private final ConsultationOpenAICaller openAICaller;
    private final ConsultationMapper consultationMapper;
    private final RequestValidator requestValidator;

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelVersion;

    @Override
    public boolean supports(AnalysisType type) {
        return AnalysisType.CAREER_CONSULTATION == type;
    }

    @Override
    public Class<ConsultationRequest> inputType() {
        return ConsultationRequest.class;
    }

    @Override
    public BirthInfo birthInfo(ConsultationRequest input) {
        return new BirthInfo(input.birthDate(), input.birthTime());
    }

    @Override
    public void validateInput(ConsultationRequest input) {
        requestValidator.validateBirthInfo(input.birthDate(), input.birthTime());
    }

    @Override
    public AnalysisOutcome<AnalysisPayload.ConsultationResultData> analyze(ConsultationRequest input,
                                                                           ManseData manseData,
                                                                           AnalysisTimeContext time) {
        SajuAnalysisFacade.SajuAnalysisContext ctx = sajuAnalysisFacade.analyze(manseData);

        CareerAdviceResponse advice = openAICaller.call(
                manseData.toFastApiResponse(input.birthDate().toString(), input.birthTime().toString()),
                ctx.tenGodDistribution(), ctx.hiddenStems(), ctx.dayMaster(), time);

        return new AnalysisOutcome<>(
                AnalysisPayload.ConsultationInput.of(input.birthDate(), input.birthTime(), time),
                consultationMapper.toResultSnapshot(manseData, ctx, advice, modelVersion, time.year()));
    }

    /**
     * 저장 직전 완성 스냅샷 검증. 조회는 이 스냅샷만 읽고 재분석하지 않으므로, 비어 있는 채로
     * 저장되면 영구히 빈 상세가 남는다.
     */
    @Override
    public void validateResult(AnalysisPayload.ConsultationResultData result) {
        if (isEmpty(result.industries()) || isEmpty(result.interviewTips()) || isEmpty(result.strengths())) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_RESULT_INVALID.getMessage());
        }
        if (result.sajuProfile() != null
                && (result.sajuProfile().dayMaster() == null || result.sajuProfile().dayMaster().isBlank()
                || isEmpty(result.sajuProfile().fiveElements()))) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_RESULT_INVALID.getMessage());
        }
        if (result.sajuProfile() == null
                || result.openaiModelVersion() == null || result.openaiModelVersion().isBlank()
                || result.analysisSummary() == null || result.analysisSummary().isBlank()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_RESULT_INVALID.getMessage());
        }
        if (result.favoredPeriod() == null || result.favoredPeriod().isBlank()
                || result.confidenceScore() < AnalysisConstants.MIN_SCORE
                || result.confidenceScore() > AnalysisConstants.MAX_SCORE) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_RESULT_INVALID.getMessage());
        }
    }

    private boolean isEmpty(java.util.Collection<?> values) {
        return values == null || values.isEmpty();
    }

    private boolean isEmpty(java.util.Map<?, ?> values) {
        return values == null || values.isEmpty();
    }
}
