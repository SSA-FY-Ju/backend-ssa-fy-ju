package ssafy.SSAju.career.caller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.provider.PromptProvider;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.exception.OpenAIApiException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConsultationOpenAICaller {

    private final ChatClient chatClient;
    private final PromptProvider promptProvider;

    /**
     * OpenAI API를 호출하여 커리어 조언을 받습니다.
     *
     * <p>Spring AI {@link ChatClient}는 내부 {@code ResponseErrorHandler}가 HTTP 응답을 먼저 가로채
     * {@link TransientAiException}(5xx 상당)/{@link NonTransientAiException}(4xx 상당)으로 변환하므로,
     * 실제로는 원본 {@code HttpServerErrorException}/{@code HttpStatusCodeException}이 이 메서드까지
     * 도달하지 않는다(실측 확인, US2/T022-2). Spring AI 자체 재시도(application.yaml
     * {@code spring.ai.retry.max-attempts: 0})는 비활성화하여 재시도 주체를 아래 {@code @Retryable}
     * 하나로 일원화한다(이중 재시도로 인한 최악 지연 방지).
     *
     * 재시도 정책 (Spring Retry):
     * - ResourceAccessException (네트워크/타임아웃): 재시도
     * - TransientAiException (OpenAI 5xx 상당, 일시적 오류): 재시도
     * - NonTransientAiException (OpenAI 4xx 상당: 401 인증 실패, 429 rate limit 등): OpenAIApiException으로 변환 후 재시도 안 함
     * - OpenAIApiException (검증 실패/4xx): 재시도 안 함 (noRetryFor)
     * - HttpMessageConversionException (역직렬화 실패): 재시도 안 함 (noRetryFor)
     */
    @Retryable(
            retryFor = {ResourceAccessException.class, TransientAiException.class},
            noRetryFor = {OpenAIApiException.class, HttpMessageConversionException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public CareerAdviceResponse call(FastAPIResponse sajuData,
                                     TenGodDistribution tenGodDistribution,
                                     HiddenStems hiddenStems,
                                     String dayMaster) {
        String prompt = promptProvider.getCareerConsultationPrompt(sajuData, tenGodDistribution, hiddenStems, dayMaster);
        CareerAdviceResponse response = OpenAIRetrySupport.callAndClassifyErrors(
                () -> chatClient.prompt().user(prompt).call().entity(CareerAdviceResponse.class),
                log);
        validate(response);
        return response;
    }

    @Recover
    public CareerAdviceResponse recoverFromTimeout(ResourceAccessException ex,
                                                   FastAPIResponse sajuData,
                                                   TenGodDistribution tenGodDistribution,
                                                   HiddenStems hiddenStems,
                                                   String dayMaster) {
        throw OpenAIRetrySupport.wrapAsTimeout(ex, log);
    }

    @Recover
    public CareerAdviceResponse recoverFromTransientError(TransientAiException ex,
                                                       FastAPIResponse sajuData,
                                                       TenGodDistribution tenGodDistribution,
                                                       HiddenStems hiddenStems,
                                                       String dayMaster) {
        throw OpenAIRetrySupport.wrapAsTransientError(ex, log);
    }

    @Recover
    public CareerAdviceResponse recoverFromOtherError(OpenAIApiException ex,
                                                      FastAPIResponse sajuData,
                                                      TenGodDistribution tenGodDistribution,
                                                      HiddenStems hiddenStems,
                                                      String dayMaster) {
        throw ex;
    }

    private void validate(CareerAdviceResponse response) {
        OpenAIRetrySupport.requireNonNullResponse(response, ErrorMessageConstants.OPENAI_EMPTY_RESPONSE);
        if (response.industries() == null || response.industries().isEmpty()) {
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_MISSING_INDUSTRIES.getMessage());
        }
        for (var industry : response.industries()) {
            if (industry == null
                    || OpenAIRetrySupport.isBlank(industry.name())
                    || OpenAIRetrySupport.isBlank(industry.reason())) {
                throw new OpenAIApiException(ErrorMessageConstants.OPENAI_INVALID_INDUSTRY_ITEM.getMessage());
            }
        }
        if (response.interviewTips() == null || response.interviewTips().isEmpty()) {
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_MISSING_INTERVIEW_TIPS.getMessage());
        }
        for (var tip : response.interviewTips()) {
            if (OpenAIRetrySupport.isBlank(tip)) {
                throw new OpenAIApiException(ErrorMessageConstants.OPENAI_INVALID_INTERVIEW_ITEM.getMessage());
            }
        }
        if (response.strengths() == null || response.strengths().isEmpty()) {
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_MISSING_STRENGTHS.getMessage());
        }
        for (var strength : response.strengths()) {
            if (OpenAIRetrySupport.isBlank(strength)) {
                throw new OpenAIApiException(ErrorMessageConstants.OPENAI_INVALID_STRENGTH_ITEM.getMessage());
            }
        }
    }
}
