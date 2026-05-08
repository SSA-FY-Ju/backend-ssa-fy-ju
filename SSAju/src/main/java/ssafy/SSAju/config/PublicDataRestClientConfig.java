package ssafy.SSAju.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import ssafy.SSAju.exception.ExternalApiException;

import java.time.Duration;

@Configuration
public class PublicDataRestClientConfig {

    @Value("${saju.public-data.url}")
    private String publicDataUrl;

    @Value("${saju.public-data.timeout-seconds}")
    private int timeoutSeconds;

    @Bean(name = "publicDataRestClient")
    public RestClient publicDataRestClient() {
        if (timeoutSeconds <= 0) {
            throw new ExternalApiException(
                    "saju.public-data.timeout-seconds 설정값은 0보다 커야 합니다. 현재 값: " + timeoutSeconds);
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return RestClient.builder()
                .baseUrl(publicDataUrl)
                .requestFactory(factory)
                .build();
    }
}
