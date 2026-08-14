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
import ssafy.SSAju.career.domain.CompatibilityNarrativeRequest;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.provider.PromptProvider;
import ssafy.SSAju.career.util.AnalysisConstants;
import ssafy.SSAju.dto.external.CompatibilityNarrativeResponse;
import ssafy.SSAju.exception.OpenAIApiException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 기업 궁합 분석 해설(8개 텍스트 필드)을 생성하는 OpenAI 1-call JSON 모드 호출 컴포넌트.
 *
 * <p>{@link ConsultationOpenAICaller}와 동일한 재시도/예외 변환 정책을 따른다.
 * 점수(궁합/직군매칭/역할별)는 이 컴포넌트가 알지 못하며, 이미 계산된 값을
 * {@link CompatibilityNarrativeRequest}로 입력받아 해설만 생성한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyMatchingOpenAICaller {

    /** {@link ConsultationOpenAICaller}와 동일한 상태 코드 복원 패턴. */
    private static final Pattern STATUS_CODE_PREFIX = Pattern.compile("^(\\d{3})\\s*-");

    private final ChatClient chatClient;
    private final PromptProvider promptProvider;

    /**
     * OpenAI API를 호출하여 기업 궁합 해설을 받습니다.
     *
     * <p>재시도 정책 (Spring Retry):
     * - ResourceAccessException (네트워크/타임아웃): 재시도
     * - TransientAiException (OpenAI 5xx 상당): 재시도
     * - NonTransientAiException (OpenAI 4xx 상당): OpenAIApiException으로 변환 후 재시도 안 함
     * - OpenAIApiException (검증 실패/4xx): 재시도 안 함 (noRetryFor)
     * - HttpMessageConversionException (역직렬화 실패): 재시도 안 함 (noRetryFor)
     */
    @Retryable(
            retryFor = {ResourceAccessException.class, TransientAiException.class},
            noRetryFor = {OpenAIApiException.class, HttpMessageConversionException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public CompatibilityNarrativeResponse call(CompatibilityNarrativeRequest request) {
        String prompt = promptProvider.getCompatibilityNarrativePrompt(request);
        CompatibilityNarrativeResponse response;
        try {
            response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(CompatibilityNarrativeResponse.class);
        } catch (OpenAIApiException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.error("OpenAI API 타임아웃, 재시도 예정");
            throw e;
        } catch (TransientAiException e) {
            log.error("OpenAI API 일시적 오류(5xx 상당), 재시도 예정");
            throw e;
        } catch (NonTransientAiException e) {
            int statusCode = extractStatusCode(e.getMessage());
            log.error("OpenAI API 클라이언트 오류(4xx 상당) statusCode={}", statusCode, e);
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(), statusCode, e);
        } catch (Exception e) {
            log.error("OpenAI API 응답 처리 실패 (재시도 불가)", e);
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(), e);
        }
        validate(response, promptProvider.currentForecastTargetMonths());
        return response;
    }

    @Recover
    public CompatibilityNarrativeResponse recoverFromTimeout(ResourceAccessException ex,
                                                              CompatibilityNarrativeRequest request) {
        log.error("OpenAI API 타임아웃: 재시도 후 최종 실패");
        throw new OpenAIApiException(ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(), ex);
    }

    @Recover
    public CompatibilityNarrativeResponse recoverFromTransientError(TransientAiException ex,
                                                                     CompatibilityNarrativeRequest request) {
        log.error("OpenAI API 일시적 오류(5xx 상당): 재시도 후 최종 실패");
        throw new OpenAIApiException(ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(), ex);
    }

    @Recover
    public CompatibilityNarrativeResponse recoverFromOtherError(OpenAIApiException ex,
                                                                 CompatibilityNarrativeRequest request) {
        throw ex;
    }

    private int extractStatusCode(String message) {
        if (message == null) {
            return 0;
        }
        Matcher matcher = STATUS_CODE_PREFIX.matcher(message);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private void validate(CompatibilityNarrativeResponse response, List<Integer> expectedTargetMonths) {
        if (response == null) {
            throw new OpenAIApiException(ErrorMessageConstants.COMPATIBILITY_NARRATIVE_EMPTY_RESPONSE.getMessage());
        }
        validateBlank(response.summary());
        validateBlank(response.roleSynergy());
        validateBlank(response.roleWarning());
        validateBlank(response.fiveElementsSynergyDescription());
        validateBlank(response.weaknessDefense());
        validateBlank(response.primaryRoleReason());
        validateBlank(response.secondaryRoleReason());

        if (response.interviewQuestions() == null || response.interviewQuestions().isEmpty()) {
            throw new OpenAIApiException(
                    ErrorMessageConstants.COMPATIBILITY_NARRATIVE_MISSING_INTERVIEW_QUESTIONS.getMessage());
        }
        for (var question : response.interviewQuestions()) {
            if (question == null
                    || isBlank(question.question())
                    || isBlank(question.intent())) {
                throw new OpenAIApiException(
                        ErrorMessageConstants.COMPATIBILITY_NARRATIVE_INVALID_INTERVIEW_ITEM.getMessage());
            }
        }

        if (response.monthlyAdvices() == null
                || response.monthlyAdvices().size() != AnalysisConstants.FORECAST_MONTH_COUNT) {
            throw new OpenAIApiException(
                    ErrorMessageConstants.COMPATIBILITY_NARRATIVE_INVALID_MONTHLY_ADVICES_COUNT.getMessage());
        }
        Set<Integer> actualMonths = new HashSet<>();
        for (var advice : response.monthlyAdvices()) {
            if (advice == null) {
                throw new OpenAIApiException(
                        ErrorMessageConstants.COMPATIBILITY_NARRATIVE_BLANK_FIELD.getMessage());
            }
            validateBlank(advice.advice());
            actualMonths.add(advice.month());
        }
        if (!actualMonths.equals(Set.copyOf(expectedTargetMonths))) {
            // 응답 리스트의 "순서"는 검증하지 않는다 — AnalysisResponseBuilder가 month 값으로
            // 매칭하므로 순서 불일치는 문제가 안 되지만, month 값 자체가 대상 월 집합과
            // 다르면(누락/중복/엉뚱한 달) 잘못된 응답이므로 거부한다.
            throw new OpenAIApiException(
                    ErrorMessageConstants.COMPATIBILITY_NARRATIVE_INVALID_MONTHLY_ADVICES_MONTHS.getMessage());
        }

        if (response.cautions() == null || response.cautions().isEmpty()) {
            throw new OpenAIApiException(ErrorMessageConstants.COMPATIBILITY_NARRATIVE_MISSING_CAUTIONS.getMessage());
        }
        for (String caution : response.cautions()) {
            validateBlank(caution);
        }
    }

    private void validateBlank(String value) {
        if (isBlank(value)) {
            throw new OpenAIApiException(ErrorMessageConstants.COMPATIBILITY_NARRATIVE_BLANK_FIELD.getMessage());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
