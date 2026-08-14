package ssafy.SSAju.career.caller;

import org.slf4j.Logger;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.OpenAIApiException;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenAI 호출 캐러({@link ConsultationOpenAICaller}, {@link CompanyMatchingOpenAICaller})가
 * 공통으로 쓰는 예외 분류/상태 코드 복원/공백 검증/빈 응답 검증 유틸리티.
 *
 * <p>{@code @Retryable}/{@code @Recover} 애노테이션 자체(재시도 정책 선언)는 Spring Retry가
 * 메서드 시그니처(파라미터 타입)로 리플렉션 매칭을 하는 특성상 상속/템플릿 메서드로 추상화하기
 * 까다로워(잘못 추상화하면 이미 검증된 재시도 타이밍 동작을 깨뜨릴 위험) 각 캐러에 그대로 둔다.
 * 다만 그 애노테이션이 감싸는 "본문 로직"(예외 분류, 상태 코드 파싱, 응답 검증)은 AOP/리플렉션과
 * 무관한 순수 로직이므로 여기로 공유해 중복을 제거한다.
 */
final class OpenAIRetrySupport {

    /**
     * Spring AI {@code RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER}가 {@code NonTransientAiException}/
     * {@code TransientAiException}의 메시지를 {@code "%s - %s"}(HTTP 상태 코드 - 응답 바디) 포맷으로
     * 구성한다는 점을 이용해 상태 코드를 복원한다. 라이브러리가 포맷을 바꿔 매칭에 실패하면 0으로
     * 폴백한다.
     */
    private static final Pattern STATUS_CODE_PREFIX = Pattern.compile("^(\\d{3})\\s*-");

    private OpenAIRetrySupport() {}

    /**
     * ChatClient 호출 + 예외 분류를 공통 처리한다. 호출부의 {@code @Retryable} 메서드 안에서
     * 실행되므로, 여기서 다시 던지는 예외는 그대로 {@code @Retryable}의 retryFor/noRetryFor
     * 판단 대상이 된다(AOP 프록시 경계를 넘지 않는 일반 메서드 호출이라 안전함).
     *
     * @param chatCall 실제 {@code chatClient.prompt().user(prompt).call().entity(...)} 호출
     * @param log      호출부(구체 캐러 클래스)의 {@code @Slf4j} 로거 — 로그에 실제 호출 클래스가 찍히도록 전달받는다
     */
    static <T> T callAndClassifyErrors(Supplier<T> chatCall, Logger log) {
        try {
            return chatCall.get();
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
            int statusCode = extractStatusCode(e.getMessage());
            log.error("OpenAI API 클라이언트 오류(4xx 상당) statusCode={}", statusCode, e);
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(), statusCode, e);
        } catch (RuntimeException e) {
            // 응답 역직렬화 실패 등 우리 쪽 코드/스키마 문제일 가능성이 커 원인 로깅
            log.error("OpenAI API 응답 처리 실패 (재시도 불가)", e);
            throw new OpenAIApiException(ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(), e);
        }
    }

    static OpenAIApiException wrapAsTimeout(ResourceAccessException ex, Logger log) {
        log.error("OpenAI API 타임아웃: 재시도 후 최종 실패");
        return new OpenAIApiException(ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(), ex);
    }

    static OpenAIApiException wrapAsTransientError(TransientAiException ex, Logger log) {
        log.error("OpenAI API 일시적 오류(5xx 상당): 재시도 후 최종 실패");
        return new OpenAIApiException(ErrorMessageConstants.OPENAI_CALL_FAILED.getMessage(), ex);
    }

    /** AI 응답 자체가 비어있는지(null) 검사한다 — 각 캐러가 자기 도메인 에러 메시지만 골라 전달한다. */
    static void requireNonNullResponse(Object response, ErrorMessageConstants emptyResponseError) {
        if (response == null) {
            throw new OpenAIApiException(emptyResponseError.getMessage());
        }
    }

    static int extractStatusCode(String message) {
        if (message == null) {
            return 0;
        }
        Matcher matcher = STATUS_CODE_PREFIX.matcher(message);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
