package ssafy.SSAju.career.caller;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.exception.OpenAIApiException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenAI 5xx 응답에 대한 실제 재시도 동작 회귀 테스트 (US2, T022-2/T024-2).
 *
 * <p>Spring AI {@link ChatClient}는 5xx 응답을 원본 {@code HttpServerErrorException}이 아니라
 * 자체 {@link TransientAiException}으로 변환한다(실측 확인). 과거
 * {@link ConsultationOpenAICaller}는 {@code HttpServerErrorException}만 재시도 대상으로
 * 분류하고 있어 실제 5xx가 재시도 없이 즉시 실패했다 — 이 테스트는 수정 후
 * {@code TransientAiException}이 재시도 대상으로 정상 분류되는지(스텁 서버 호출 횟수로 확인)를 검증한다.
 */
@SpringBootTest
@DisplayName("OpenAI 5xx 응답 재시도 회귀 테스트 (US2)")
class ConsultationOpenAICallerServerErrorInvestigationTest {

    private static HttpServer stubServer;
    private static final AtomicInteger hitCount = new AtomicInteger(0);

    @BeforeAll
    static void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/", exchange -> {
            hitCount.incrementAndGet();
            byte[] body = "{\"error\":\"internal error\"}".getBytes();
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        stubServer.start();
    }

    @AfterAll
    static void stopStubServer() {
        stubServer.stop(0);
    }

    @BeforeEach
    void resetHitCount() {
        hitCount.set(0);
    }

    @DynamicPropertySource
    static void serverErrorEndpoint(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + stubServer.getAddress().getPort());
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
    @DisplayName("ChatClient 원시 호출: OpenAI 500 응답은 TransientAiException으로 변환된다")
    void chatClient_serverError_convertedToTransientAiException() {
        assertThatThrownBy(() -> chatClient.prompt()
                .user("테스트 프롬프트")
                .call()
                .entity(CareerAdviceResponse.class))
                .isInstanceOf(TransientAiException.class);
    }

    @Test
    @DisplayName("ConsultationOpenAICaller.call: 500 응답은 재시도(3회) 후 OpenAIApiException으로 실패한다")
    void consultationOpenAICaller_serverError_retriesThenFails() {
        assertThatThrownBy(() -> consultationOpenAICaller.call(SAJU_DATA, TEN_GOD, HIDDEN_STEMS, "己"))
                .isInstanceOf(OpenAIApiException.class);

        // @Retryable(maxAttempts=3)이 TransientAiException을 재시도 대상으로 인식했다면 스텁 서버가 3번 호출된다.
        // (수정 전 버그: catch-all로 떨어져 재시도 없이 1번만 호출되고 즉시 실패)
        assertThat(hitCount.get()).isEqualTo(3);
    }
}
