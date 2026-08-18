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

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("NonTransientAiException(401) → OpenAIApiException에 statusCode 401이 보존된다")
    void nonTransientAiException_preservesStatusCode401() {
        // Spring AI RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER가 실제로 구성하는 포맷: "%s - %s"
        given(chatClient.prompt().user(anyString()).call().entity(CareerAdviceResponse.class))
                .willThrow(new NonTransientAiException("401 - Incorrect API key provided"));

        assertThatThrownBy(() -> caller.call(SAJU_DATA, TEN_GOD, HIDDEN_STEMS, "己"))
                .isInstanceOf(OpenAIApiException.class)
                .extracting(ex -> ((OpenAIApiException) ex).getStatusCode())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("NonTransientAiException(429) → OpenAIApiException에 statusCode 429가 보존된다")
    void nonTransientAiException_preservesStatusCode429() {
        given(chatClient.prompt().user(anyString()).call().entity(CareerAdviceResponse.class))
                .willThrow(new NonTransientAiException("429 - Rate limit reached for requests"));

        assertThatThrownBy(() -> caller.call(SAJU_DATA, TEN_GOD, HIDDEN_STEMS, "己"))
                .isInstanceOf(OpenAIApiException.class)
                .extracting(ex -> ((OpenAIApiException) ex).getStatusCode())
                .isEqualTo(429);
    }

    @Test
    @DisplayName("NonTransientAiException 메시지가 상태 코드 포맷이 아니면 statusCode는 0으로 폴백한다")
    void nonTransientAiException_fallsBackToZero_whenMessageHasNoStatusCodePrefix() {
        given(chatClient.prompt().user(anyString()).call().entity(CareerAdviceResponse.class))
                .willThrow(new NonTransientAiException("Unauthorized"));

        assertThatThrownBy(() -> caller.call(SAJU_DATA, TEN_GOD, HIDDEN_STEMS, "己"))
                .isInstanceOf(OpenAIApiException.class)
                .extracting(ex -> ((OpenAIApiException) ex).getStatusCode())
                .isEqualTo(0);
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

    // ─────────────────────────────────────────
    // T019: validate()의 기존 null/blank/필수 컬렉션 검사 회귀 확인
    // (JSON 마이그레이션으로 CareerAdviceResponse 필드 구조 자체는 변경되지 않았으므로
    // 검증 로직도 변경 없이 그대로 동작해야 한다)
    // ─────────────────────────────────────────

    private static CareerAdviceResponse advice(
            java.util.List<CareerAdviceResponse.IndustryRecommendation> industries,
            java.util.List<String> interviewTips,
            java.util.List<String> strengths) {
        return new CareerAdviceResponse(
                industries, interviewTips, strengths, List.of(),
                null, null, null, null, null, null, null, null, null,
                List.of(), "일간 설명", "오행 설명"
        );
    }

    @Test
    @DisplayName("industries가 비어있으면 OpenAIApiException")
    void emptyIndustries_throwsOpenAIApiException() {
        CareerAdviceResponse response = advice(List.of(), List.of("팁"), List.of("강점"));
        given(chatClient.prompt().user(anyString()).call().entity(CareerAdviceResponse.class))
                .willReturn(response);

        assertThatThrownBy(() -> caller.call(SAJU_DATA, TEN_GOD, HIDDEN_STEMS, "己"))
                .isInstanceOf(OpenAIApiException.class);
    }

    @Test
    @DisplayName("industries 항목의 name/reason이 blank면 OpenAIApiException")
    void blankIndustryItem_throwsOpenAIApiException() {
        CareerAdviceResponse response = advice(
                List.of(new CareerAdviceResponse.IndustryRecommendation("  ", "이유", List.of())),
                List.of("팁"), List.of("강점"));
        given(chatClient.prompt().user(anyString()).call().entity(CareerAdviceResponse.class))
                .willReturn(response);

        assertThatThrownBy(() -> caller.call(SAJU_DATA, TEN_GOD, HIDDEN_STEMS, "己"))
                .isInstanceOf(OpenAIApiException.class);
    }

    @Test
    @DisplayName("interviewTips가 비어있으면 OpenAIApiException")
    void emptyInterviewTips_throwsOpenAIApiException() {
        CareerAdviceResponse response = advice(
                List.of(new CareerAdviceResponse.IndustryRecommendation("IT", "이유", List.of())),
                List.of(), List.of("강점"));
        given(chatClient.prompt().user(anyString()).call().entity(CareerAdviceResponse.class))
                .willReturn(response);

        assertThatThrownBy(() -> caller.call(SAJU_DATA, TEN_GOD, HIDDEN_STEMS, "己"))
                .isInstanceOf(OpenAIApiException.class);
    }

    @Test
    @DisplayName("strengths가 비어있으면 OpenAIApiException")
    void emptyStrengths_throwsOpenAIApiException() {
        CareerAdviceResponse response = advice(
                List.of(new CareerAdviceResponse.IndustryRecommendation("IT", "이유", List.of())),
                List.of("팁"), List.of());
        given(chatClient.prompt().user(anyString()).call().entity(CareerAdviceResponse.class))
                .willReturn(response);

        assertThatThrownBy(() -> caller.call(SAJU_DATA, TEN_GOD, HIDDEN_STEMS, "己"))
                .isInstanceOf(OpenAIApiException.class);
    }

    @Test
    @DisplayName("모든 필드가 유효하면 정상적으로 응답을 반환한다")
    void validResponse_returnsAsIs() {
        CareerAdviceResponse response = advice(
                List.of(new CareerAdviceResponse.IndustryRecommendation("IT", "이유", List.of("백엔드"))),
                List.of("팁"), List.of("강점"));
        given(chatClient.prompt().user(anyString()).call().entity(CareerAdviceResponse.class))
                .willReturn(response);

        CareerAdviceResponse result = caller.call(SAJU_DATA, TEN_GOD, HIDDEN_STEMS, "己");

        assertThat(result.industries()).hasSize(1);
        assertThat(result.strengths()).containsExactly("강점");
    }
}
