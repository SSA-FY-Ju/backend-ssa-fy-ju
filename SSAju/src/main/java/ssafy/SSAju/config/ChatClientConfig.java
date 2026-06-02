package ssafy.SSAju.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * OpenAI ChatClient 설정.
 *
 * <p>{@link RestClientCustomizer}를 통해 Spring AI 내부 RestClient에 타임아웃을 주입합니다.
 * Spring AI의 {@code OpenAiChatAutoConfiguration}이 {@code ObjectProvider<RestClient.Builder>}로
 * 이 커스터마이저가 적용된 빌더를 주입받아 {@code OpenAiApi}를 생성합니다.
 *
 * <p>{@code FastApiRestClientConfig}·{@code PublicDataRestClientConfig}처럼
 * {@code RestClient.builder()}를 직접 호출하는 빈은 커스터마이저 체인을 거치지 않으므로 영향받지 않습니다.
 *
 * <p>타임아웃 값은 {@code saju.openai.timeout-seconds} (기본값: 8초)에서 읽습니다.
 */
@Configuration
public class ChatClientConfig {

    @Value("${saju.openai.timeout-seconds}")
    private int openaiTimeoutSeconds;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * Spring AI OpenAI 내부 RestClient에 read/connect timeout을 적용합니다.
     *
     * <p>OpenAI 응답은 LLM 특성상 지연이 길 수 있어 타임아웃 미설정 시
     * 톰캣 워커 스레드가 무한정 점유될 위험이 있습니다.
     */
    @Bean
    public RestClientCustomizer openAiTimeoutCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setReadTimeout(Duration.ofSeconds(openaiTimeoutSeconds));
            factory.setConnectTimeout(Duration.ofSeconds(openaiTimeoutSeconds));
            builder.requestFactory(factory);
        };
    }
}
