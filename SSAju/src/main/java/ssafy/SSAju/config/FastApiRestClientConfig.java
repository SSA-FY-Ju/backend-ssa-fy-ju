package ssafy.SSAju.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class FastApiRestClientConfig {

    @Value("${saju.fastapi.url}")
    private String fastApiUrl;

    @Value("${saju.fastapi.timeout-seconds}")
    private int fastApiTimeoutSeconds;

    @Bean(name = "fastApiRestClient")
    public RestClient fastApiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(fastApiTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(fastApiTimeoutSeconds));
        return RestClient.builder()
                .baseUrl(fastApiUrl)
                .requestFactory(factory)
                .build();
    }
}
