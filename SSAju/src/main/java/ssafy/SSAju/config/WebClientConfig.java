package ssafy.SSAju.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${saju.fastapi.url}")
    private String fastApiUrl;

    @Value("${saju.fastapi.timeout-seconds}")
    private int fastApiTimeoutSeconds;

    @Value("${saju.public-data.url}")
    private String publicDataUrl;

    @Value("${saju.public-data.timeout-seconds}")
    private int publicDataTimeoutSeconds;

    @Bean(name = "fastApiWebClient")
    public WebClient fastApiWebClient() {
        return WebClient.builder()
                .baseUrl(fastApiUrl)
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient(fastApiTimeoutSeconds)))
                .build();
    }

    @Bean(name = "publicDataWebClient")
    public WebClient publicDataWebClient() {
        return WebClient.builder()
                .baseUrl(publicDataUrl)
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient(publicDataTimeoutSeconds)))
                .build();
    }

    private HttpClient buildHttpClient(int timeoutSeconds) {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds * 1000)
                .responseTimeout(Duration.ofSeconds(timeoutSeconds));
    }
}
