package ssafy.SSAju.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import ssafy.SSAju.exception.ExternalApiException;

import java.time.Duration;

@Configuration
public class FastApiRestClientConfig {

    @Value("${saju.fastapi.url}")
    private String fastApiUrl;

    @Value("${saju.fastapi.timeout-seconds}")
    private int fastApiTimeoutSeconds;

    @Bean(name = "fastApiRestClient")
    public RestClient fastApiRestClient() {
        if (fastApiTimeoutSeconds <= 0) {
            throw new ExternalApiException(
                    "saju.fastapi.timeout-seconds 설정값은 0보다 커야 합니다. 현재 값: " + fastApiTimeoutSeconds);
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(fastApiTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(fastApiTimeoutSeconds));
        return RestClient.builder()
                .baseUrl(fastApiUrl)
                .requestFactory(factory)
                .build();
    }
}
