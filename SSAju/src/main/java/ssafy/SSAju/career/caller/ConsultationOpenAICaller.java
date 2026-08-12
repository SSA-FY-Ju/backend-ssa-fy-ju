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
        CareerAdviceResponse response;
        try {
            response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(CareerAdviceResponse.class);
        } catch (OpenAIApiException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.error("OpenAI API 타임아웃, 재시도 예정");
            throw e;
        } catch (TransientAiException e) {
            log.error("OpenAI API 일시적 오류(5xx 상당), 재시도 예정");
            throw e;
        } catch (NonTransientAiException e) {
            // 4xx 상당 (401 인증 실패, 429 rate limit 등): 재시도 불가, 우리 쪽 설정/사용량 문제일 가능성이 커 원인 로깅
            log.error("OpenAI API 클라이언트 오류(4xx 상당)", e);
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(), e);
        } catch (Exception e) {
            // 응답 역직렬화 실패 등 우리 쪽 코드/스키마 문제일 가능성이 커 원인 로깅
            log.error("OpenAI API 응답 처리 실패 (재시도 불가)", e);
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(), e);
        }
        validate(response);
        return response;
    }

    @Recover
    public CareerAdviceResponse recoverFromTimeout(ResourceAccessException ex,
                                                   FastAPIResponse sajuData,
                                                   TenGodDistribution tenGodDistribution,
                                                   HiddenStems hiddenStems,
                                                   String dayMaster) {
        log.error("OpenAI API 타임아웃: 재시도 후 최종 실패");
        throw new OpenAIApiException(ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(), ex);
    }

    @Recover
    public CareerAdviceResponse recoverFromTransientError(TransientAiException ex,
                                                       FastAPIResponse sajuData,
                                                       TenGodDistribution tenGodDistribution,
                                                       HiddenStems hiddenStems,
                                                       String dayMaster) {
        log.error("OpenAI API 일시적 오류(5xx 상당): 재시도 후 최종 실패");
        throw new OpenAIApiException(ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(), ex);
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
        if (response == null) {
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_EMPTY_RESPONSE.getMessage());
        }
        if (response.industries() == null || response.industries().isEmpty()) {
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_MISSING_INDUSTRIES.getMessage());
        }
        for (var industry : response.industries()) {
            if (industry == null
                    || industry.name() == null || industry.name().isBlank()
                    || industry.reason() == null || industry.reason().isBlank()) {
                throw new OpenAIApiException(ErrorMessageConstants.OPENAI_INVALID_INDUSTRY_ITEM.getMessage());
            }
        }
        if (response.interviewTips() == null || response.interviewTips().isEmpty()) {
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_MISSING_INTERVIEW_TIPS.getMessage());
        }
        for (var tip : response.interviewTips()) {
            if (tip == null || tip.isBlank()) {
                throw new OpenAIApiException(ErrorMessageConstants.OPENAI_INVALID_INTERVIEW_ITEM.getMessage());
            }
        }
        if (response.strengths() == null || response.strengths().isEmpty()) {
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_MISSING_STRENGTHS.getMessage());
        }
        for (var strength : response.strengths()) {
            if (strength == null || strength.isBlank()) {
                throw new OpenAIApiException(ErrorMessageConstants.OPENAI_INVALID_STRENGTH_ITEM.getMessage());
            }
        }
    }
}
