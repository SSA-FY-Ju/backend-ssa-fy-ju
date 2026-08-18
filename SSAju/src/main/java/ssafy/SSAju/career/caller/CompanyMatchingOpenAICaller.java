package ssafy.SSAju.career.caller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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
import ssafy.SSAju.career.util.ForecastMonthCalculator;
import ssafy.SSAju.dto.external.CompatibilityNarrativeResponse;
import ssafy.SSAju.exception.OpenAIApiException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 기업 궁합 분석 해설(8개 텍스트 필드)을 생성하는 OpenAI 1-call JSON 모드 호출 컴포넌트.
 *
 * <p>{@link ConsultationOpenAICaller}와 동일한 재시도/예외 변환 정책을 따른다(상태 코드 복원/
 * 공백 검증 로직은 {@link OpenAIRetrySupport}로 공유). 점수(궁합/직군매칭/역할별)는 이 컴포넌트가
 * 알지 못하며, 이미 계산된 값을 {@link CompatibilityNarrativeRequest}로 입력받아 해설만 생성한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyMatchingOpenAICaller {

    private final ChatClient chatClient;
    private final PromptProvider promptProvider;
    private final ForecastMonthCalculator forecastMonthCalculator;

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
        // 프롬프트 생성과 응답 검증이 같은 "대상 월" 기준을 쓰도록 한 번만 계산해 재사용한다.
        // 네트워크 호출 전후로 각각 다시 계산하면 자정/월 경계를 넘는 순간 두 기준이 어긋날 수 있다.
        List<Integer> targetMonths = forecastMonthCalculator.currentTargetMonths();
        String prompt = promptProvider.getCompatibilityNarrativePrompt(request, targetMonths);
        CompatibilityNarrativeResponse response = OpenAIRetrySupport.callAndClassifyErrors(
                () -> chatClient.prompt().user(prompt).call().entity(CompatibilityNarrativeResponse.class),
                log);
        validate(response, targetMonths);
        return response;
    }

    @Recover
    public CompatibilityNarrativeResponse recoverFromTimeout(ResourceAccessException ex,
                                                              CompatibilityNarrativeRequest request) {
        throw OpenAIRetrySupport.wrapAsTimeout(ex, log);
    }

    @Recover
    public CompatibilityNarrativeResponse recoverFromTransientError(TransientAiException ex,
                                                                     CompatibilityNarrativeRequest request) {
        throw OpenAIRetrySupport.wrapAsTransientError(ex, log);
    }

    @Recover
    public CompatibilityNarrativeResponse recoverFromOtherError(OpenAIApiException ex,
                                                                 CompatibilityNarrativeRequest request) {
        throw ex;
    }

    /**
     * month 범위는 아래 monthlyAdvices 검증(대상월 집합 완전 일치)이 이미 포괄한다 — 범위를 벗어나거나
     * 중복된 month는 이 일치 검사에서 자동으로 거부되므로 별도 range 검사를 추가하지 않는다.
     *
     * <p>{@code TargetRoleAnalysis.matchScore}/{@code MonthlyForecast.score}는 이 메서드가 검증하는
     * {@link CompatibilityNarrativeResponse}에 필드로 존재하지 않는다 — OpenAI 응답이 아니라
     * {@code JobRoleAnalyzer}/{@code AnalysisResponseBuilder}가 오행 데이터로 내부 계산하는 값이라
     * 이 저장-전 검증의 대상이 아니다(계산 공식 자체가 0~100으로 자체 유계).
     */
    private void validate(CompatibilityNarrativeResponse response, List<Integer> expectedTargetMonths) {
        OpenAIRetrySupport.requireNonNullResponse(
                response, ErrorMessageConstants.COMPATIBILITY_NARRATIVE_EMPTY_RESPONSE);
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
                    || OpenAIRetrySupport.isBlank(question.question())
                    || OpenAIRetrySupport.isBlank(question.intent())) {
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
        if (OpenAIRetrySupport.isBlank(value)) {
            throw new OpenAIApiException(ErrorMessageConstants.COMPATIBILITY_NARRATIVE_BLANK_FIELD.getMessage());
        }
    }
}
