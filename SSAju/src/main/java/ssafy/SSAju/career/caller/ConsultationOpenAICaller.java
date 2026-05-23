package ssafy.SSAju.career.caller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
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
     * 재시도 정책 (Spring Retry):
     * - ResourceAccessException (네트워크/타임아웃): 재시도
     * - HttpServerErrorException (5xx): 재시도
     * - 4xx (401 인증 실패, 429 rate limit 등): OpenAIApiException으로 변환 후 재시도 안 함
     * - OpenAIApiException (검증 실패/4xx): 재시도 안 함 (noRetryFor)
     * - HttpMessageConversionException (역직렬화 실패): 재시도 안 함 (noRetryFor)
     */
    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
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
        } catch (HttpServerErrorException e) {
            log.error("OpenAI API 5xx 오류 statusCode={}, 재시도 예정", e.getStatusCode());
            throw e;
        } catch (HttpStatusCodeException e) {
            // 4xx (401 인증 실패, 429 rate limit 등): 재시도 불가
            log.error("OpenAI API 클라이언트 오류 statusCode={}", e.getStatusCode());
            throw new OpenAIApiException(
                    ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(),
                    e.getStatusCode().value(), e);
        } catch (Exception e) {
            log.error("OpenAI API 응답 처리 실패 (재시도 불가)");
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
    public CareerAdviceResponse recoverFromServerError(HttpServerErrorException ex,
                                                       FastAPIResponse sajuData,
                                                       TenGodDistribution tenGodDistribution,
                                                       HiddenStems hiddenStems,
                                                       String dayMaster) {
        log.error("OpenAI API 5xx 오류: 재시도 후 최종 실패 statusCode={}", ex.getStatusCode());
        throw new OpenAIApiException(
                ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(),
                ex.getStatusCode().value(), ex);
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
