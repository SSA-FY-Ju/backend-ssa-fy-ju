package ssafy.SSAju.career.caller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenAI 호출 캐러({@link ConsultationOpenAICaller}, {@link CompanyMatchingOpenAICaller})가
 * 공통으로 쓰는 상태 코드 복원/공백 검증 유틸리티.
 *
 * <p>재시도 정책({@code @Retryable}/{@code @Recover})은 각 캐러의 메서드 시그니처(파라미터 타입)에
 * 묶여 있어 Spring Retry의 리플렉션 기반 매칭 특성상 상속/템플릿 메서드로 추상화하기 까다롭다
 * (잘못 추상화하면 이미 검증된 재시도 타이밍 동작을 깨뜨릴 위험). 그래서 그 부분은 각 캐러에
 * 그대로 두고, AOP/리플렉션과 무관한 순수 로직만 이 유틸리티로 공유해 중복을 제거한다.
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
