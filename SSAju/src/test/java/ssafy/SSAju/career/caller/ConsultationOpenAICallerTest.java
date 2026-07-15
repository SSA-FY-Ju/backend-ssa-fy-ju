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
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.career.provider.PromptProvider;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
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
 * ConsultationOpenAICaller의 예외 분류 단위 테스트 (US2, T024-2).
 *
 * <p>Spring AI {@link ChatClient}의 실제 응답이 {@link TransientAiException}(5xx 상당)/
 * {@link NonTransientAiException}(4xx 상당)으로 도착하는 것을 실측으로 확인했으므로,
 * 그 타입 기준으로 재시도 대상/즉시 실패 분기가 올바르게 분류되는지 검증한다.
 *
 * <p>이 테스트는 {@code new}로 직접 생성한 순수 객체를 사용하므로 {@code @Retryable} AOP는
 * 적용되지 않는다 — 실제 재시도 횟수/최종 변환({@code @Recover})까지 포함한 검증은
 * {@link ConsultationOpenAICallerRetryTest}, {@link ConsultationOpenAICallerServerErrorInvestigationTest}
 * (실제 Spring 컨텍스트 + AOP 프록시)에서 수행한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConsultationOpenAICaller 단위 테스트")
class ConsultationOpenAICallerTest {

    @Mock
    private PromptProvider promptProvider;

    private ChatClient chatClient;
    private ConsultationOpenAICaller caller;

    private static final FastAPIResponse SAJU_DATA = new FastAPIResponse(
            List.of("庚", "丙", "己", "辛"),
            List.of("午", "戌", "未", "寅"),
            Map.of("木", 1, "火", 2, "土", 1, "金", 2, "水", 2),
            "庚午", "丙戌", "己未", "辛寅",
            "14:30", "1990-10-10", null
    );
    private static final TenGodDistribution TEN_GOD = new TenGodDistribution(Map.of("정관", 1));
    private static final HiddenStems HIDDEN_STEMS = new HiddenStems(Map.of("午", List.of("丁")));

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        caller = new ConsultationOpenAICaller(chatClient, promptProvider);
        given(promptProvider.getCareerConsultationPrompt(any(), any(), any(), any())).willReturn("prompt");
    }

    @Test
    @DisplayName("NonTransientAiException(4xx 상당) → 재시도 없이 즉시 OpenAIApiException")
    void nonTransientAiException_failsImmediately() {
        given(chatClient.prompt().user(anyString()).call().entity(CareerAdviceResponse.class))
                .willThrow(new NonTransientAiException("401 Unauthorized"));

        assertThatThrownBy(() -> caller.call(SAJU_DATA, TEN_GOD, HIDDEN_STEMS, "己"))
                .isInstanceOf(OpenAIApiException.class);
    }

    @Test
    @DisplayName("TransientAiException(5xx 상당) → 변환하지 않고 그대로 재전파 (재시도 대상으로 분류됨을 의미)")
    void transientAiException_propagatesAsIs_forRetry() {
        given(chatClient.prompt().user(anyString()).call().entity(CareerAdviceResponse.class))
                .willThrow(new TransientAiException("500 Internal Server Error"));

        // AOP(@Retryable) 없이 직접 호출하므로, 여기서 원본 TransientAiException이 그대로 나와야
        // @Retryable의 retryFor 목록에 포함되어 정상적으로 재시도 대상이 됨을 보장한다.
        assertThatThrownBy(() -> caller.call(SAJU_DATA, TEN_GOD, HIDDEN_STEMS, "己"))
                .isInstanceOf(TransientAiException.class);
    }
}
