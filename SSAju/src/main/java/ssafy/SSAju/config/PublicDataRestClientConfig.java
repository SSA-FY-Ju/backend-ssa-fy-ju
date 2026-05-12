package ssafy.SSAju.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import ssafy.SSAju.exception.ExternalApiException;

import java.net.http.HttpClient;
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
        // JDK 내장 HttpClient: 커넥션 풀링 지원, 추가 의존성 없음 (JDK 11+)
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return RestClient.builder()
                .baseUrl(publicDataUrl)
                .requestFactory(factory)
                .build();
    }
}
