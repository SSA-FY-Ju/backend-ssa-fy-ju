package ssafy.SSAju.career.caller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import ssafy.SSAju.career.domain.CompatibilityNarrativeRequest;
import ssafy.SSAju.career.domain.FiveElements;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.provider.PromptProvider;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.dto.external.CompatibilityNarrativeResponse;
import ssafy.SSAju.exception.OpenAIApiException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/**
 * CompanyMatchingOpenAICaller의 예외 분류/응답 검증 단위 테스트 (US1, T002).
 *
 * <p>{@link ConsultationOpenAICallerTest}와 동일한 패턴 — {@code new}로 직접 생성한 순수 객체를
 * 사용하므로 {@code @Retryable} AOP는 적용되지 않는다. 재시도 횟수까지 포함한 검증은
 * 별도의 {@code @SpringBootTest} 기반 테스트에서 수행한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CompanyMatchingOpenAICaller 단위 테스트")
class CompanyMatchingOpenAICallerTest {

    @Mock
    private PromptProvider promptProvider;

    private ChatClient chatClient;
    private CompanyMatchingOpenAICaller caller;

    private static final FiveElements FIVE_ELEMENTS =
            new FiveElements(Map.of("木", 1, "火", 2, "土", 1, "金", 2, "水", 2));
    private static final HiddenStems HIDDEN_STEMS = new HiddenStems(Map.of("午", List.of("丁")));

    private static final CompatibilityNarrativeRequest REQUEST = new CompatibilityNarrativeRequest(
            FIVE_ELEMENTS, HIDDEN_STEMS, "己",
            FIVE_ELEMENTS, HIDDEN_STEMS, "庚",
            80, 70, 70, 55,
            JobCategoryEnum.TECH_BACKEND, "백엔드 개발자"
    );

    private static final CompatibilityNarrativeResponse.InterviewQuestion VALID_QUESTION =
            new CompatibilityNarrativeResponse.InterviewQuestion("질문", "의도");

    private static CompatibilityNarrativeResponse validResponse() {
        return new CompatibilityNarrativeResponse(
                "요약", "시너지", "경고", "오행 시너지", "약점 방어",
                List.of(VALID_QUESTION), "전문가 사유", "리드 사유",
                List.of("1월", "2월", "3월", "4월", "5월"),
                List.of("주의사항")
        );
    }

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        caller = new CompanyMatchingOpenAICaller(chatClient, promptProvider);
        given(promptProvider.getCompatibilityNarrativePrompt(any())).willReturn("prompt");
    }

    @Test
    @DisplayName("NonTransientAiException(4xx 상당) → 재시도 없이 즉시 OpenAIApiException")
    void nonTransientAiException_failsImmediately() {
        given(chatClient.prompt().user(anyString()).call().entity(CompatibilityNarrativeResponse.class))
                .willThrow(new NonTransientAiException("401 Unauthorized"));

        assertThatThrownBy(() -> caller.call(REQUEST))
                .isInstanceOf(OpenAIApiException.class);
    }

    @Test
    @DisplayName("TransientAiException(5xx 상당) → 변환하지 않고 그대로 재전파 (재시도 대상으로 분류됨을 의미)")
    void transientAiException_propagatesAsIs_forRetry() {
        given(chatClient.prompt().user(anyString()).call().entity(CompatibilityNarrativeResponse.class))
                .willThrow(new TransientAiException("500 Internal Server Error"));

        assertThatThrownBy(() -> caller.call(REQUEST))
                .isInstanceOf(TransientAiException.class);
    }

    @Test
    @DisplayName("응답이 null이면 OpenAIApiException")
    void nullResponse_throwsOpenAIApiException() {
        given(chatClient.prompt().user(anyString()).call().entity(CompatibilityNarrativeResponse.class))
                .willReturn(null);

        assertThatThrownBy(() -> caller.call(REQUEST))
                .isInstanceOf(OpenAIApiException.class);
    }

    @Test
    @DisplayName("필수 텍스트 필드(summary)가 비어있으면 OpenAIApiException")
    void blankSummary_throwsOpenAIApiException() {
        CompatibilityNarrativeResponse response = new CompatibilityNarrativeResponse(
                "  ", "시너지", "경고", "오행 시너지", "약점 방어",
                List.of(VALID_QUESTION), "전문가 사유", "리드 사유",
                List.of("1월", "2월", "3월", "4월", "5월"),
                List.of("주의사항")
        );
        given(chatClient.prompt().user(anyString()).call().entity(CompatibilityNarrativeResponse.class))
                .willReturn(response);

        assertThatThrownBy(() -> caller.call(REQUEST))
                .isInstanceOf(OpenAIApiException.class);
    }

    @Test
    @DisplayName("interviewQuestions가 비어있으면 OpenAIApiException")
    void emptyInterviewQuestions_throwsOpenAIApiException() {
        CompatibilityNarrativeResponse response = new CompatibilityNarrativeResponse(
                "요약", "시너지", "경고", "오행 시너지", "약점 방어",
                List.of(), "전문가 사유", "리드 사유",
                List.of("1월", "2월", "3월", "4월", "5월"),
                List.of("주의사항")
        );
        given(chatClient.prompt().user(anyString()).call().entity(CompatibilityNarrativeResponse.class))
                .willReturn(response);

        assertThatThrownBy(() -> caller.call(REQUEST))
                .isInstanceOf(OpenAIApiException.class);
    }

    @Test
    @DisplayName("monthlyAdvices가 5개가 아니면 OpenAIApiException")
    void monthlyAdvicesWrongCount_throwsOpenAIApiException() {
        CompatibilityNarrativeResponse response = new CompatibilityNarrativeResponse(
                "요약", "시너지", "경고", "오행 시너지", "약점 방어",
                List.of(VALID_QUESTION), "전문가 사유", "리드 사유",
                List.of("1월", "2월"),
                List.of("주의사항")
        );
        given(chatClient.prompt().user(anyString()).call().entity(CompatibilityNarrativeResponse.class))
                .willReturn(response);

        assertThatThrownBy(() -> caller.call(REQUEST))
                .isInstanceOf(OpenAIApiException.class);
    }

    @Test
    @DisplayName("cautions가 비어있으면 OpenAIApiException")
    void emptyCautions_throwsOpenAIApiException() {
        CompatibilityNarrativeResponse response = new CompatibilityNarrativeResponse(
                "요약", "시너지", "경고", "오행 시너지", "약점 방어",
                List.of(VALID_QUESTION), "전문가 사유", "리드 사유",
                List.of("1월", "2월", "3월", "4월", "5월"),
                List.of()
        );
        given(chatClient.prompt().user(anyString()).call().entity(CompatibilityNarrativeResponse.class))
                .willReturn(response);

        assertThatThrownBy(() -> caller.call(REQUEST))
                .isInstanceOf(OpenAIApiException.class);
    }

    @Test
    @DisplayName("모든 필드가 유효하면 정상적으로 응답을 반환한다")
    void validResponse_returnsAsIs() {
        given(chatClient.prompt().user(anyString()).call().entity(CompatibilityNarrativeResponse.class))
                .willReturn(validResponse());

        CompatibilityNarrativeResponse result = caller.call(REQUEST);

        org.assertj.core.api.Assertions.assertThat(result.summary()).isEqualTo("요약");
        org.assertj.core.api.Assertions.assertThat(result.monthlyAdvices()).hasSize(5);
    }
}
