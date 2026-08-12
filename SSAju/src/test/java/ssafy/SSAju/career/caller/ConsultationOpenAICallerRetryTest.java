package ssafy.SSAju.career.caller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.ResourceAccessException;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.exception.OpenAIApiException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenAI 연결 실패 시 실제 예외 타입 및 총 재시도 소요시간 회귀 테스트 (US2, T022-2/T024-2).
 *
 * <p>과거 Spring AI 자체 내부 재시도(기본 max-attempts=10)와
 * {@link ConsultationOpenAICaller}의 {@code @Retryable}(maxAttempts=3)이 이중으로 겹쳐
 * 연결 실패 시 최악 약 75분이 소요되던 문제가 실측으로 확인되었다.
 * {@code spring.ai.retry.max-attempts: 0}(test/application.yaml)으로 Spring AI 자체 재시도를
 * 비활성화한 뒤에는 재시도 주체가 {@code @Retryable} 하나뿐이므로 총 소요시간이
 * backoff 예산(1s+2s=3s) 수준으로 돌아와야 한다.
 */
@SpringBootTest
@DisplayName("OpenAI 연결 실패 재시도 회귀 테스트 (US2)")
class ConsultationOpenAICallerRetryTest {

    @DynamicPropertySource
    static void unreachableOpenAiEndpoint(DynamicPropertyRegistry registry) {
        // 연결이 즉시 거부되는 로컬 포트로 지정 — 네트워크 지연 없이 연결 실패를 재현
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:1");
    }

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ConsultationOpenAICaller consultationOpenAICaller;

    private static final FastAPIResponse SAJU_DATA = new FastAPIResponse(
            List.of("庚", "丙", "己", "辛"),
            List.of("午", "戌", "未", "寅"),
            Map.of("木", 1, "火", 2, "土", 1, "金", 2, "水", 2),
            "庚午", "丙戌", "己未", "辛寅",
            "14:30", "1990-10-10", null
    );
    private static final TenGodDistribution TEN_GOD = new TenGodDistribution(Map.of("정관", 1));
    private static final HiddenStems HIDDEN_STEMS = new HiddenStems(Map.of("午", List.of("丁")));

    @Test
    @DisplayName("ChatClient 원시 호출: 연결 실패 시 ResourceAccessException이 그대로 전파된다")
    void chatClient_connectionFailure_propagatesResourceAccessException() {
        assertThatThrownBy(() -> chatClient.prompt()
                .user("테스트 프롬프트")
                .call()
                .entity(CareerAdviceResponse.class))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    @DisplayName("ConsultationOpenAICaller.call: 연결 실패 시 재시도(3회) 후 OpenAIApiException, 총 소요시간은 수 초 내")
    void consultationOpenAICaller_connectionFailure_failsFastWithoutDoubleRetry() {
        long start = System.currentTimeMillis();

        assertThatThrownBy(() -> consultationOpenAICaller.call(SAJU_DATA, TEN_GOD, HIDDEN_STEMS, "己"))
                .isInstanceOf(OpenAIApiException.class);

        long elapsedMs = System.currentTimeMillis() - start;
        // 우리 @Retryable(maxAttempts=3, backoff 1s→2s)만 적용되면 최소 3s, 넉넉히 잡아도 30s 이내여야 한다.
        // Spring AI 자체 재시도(기본 10회, 최대 22분)가 다시 겹치면 이 값을 훨씬 초과한다.
        assertThat(elapsedMs).isLessThan(30_000);
    }
}
