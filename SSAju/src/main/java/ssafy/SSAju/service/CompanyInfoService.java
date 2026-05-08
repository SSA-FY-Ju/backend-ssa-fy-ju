package ssafy.SSAju.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ssafy.SSAju.exception.PublicDataApiException;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 공공데이터 API를 통해 기업 설립일을 조회합니다.
 * 조회 실패 시 PublicDataApiException을 던져 사용자가 직접 입력하도록 fallback 처리합니다.
 */
@Slf4j
@Service
public class CompanyInfoService {

    private final RestClient publicDataRestClient;

    @Value("${saju.public-data.api-key}")
    private String apiKey;

    public CompanyInfoService(@Qualifier("publicDataRestClient") RestClient publicDataRestClient) {
        this.publicDataRestClient = publicDataRestClient;
    }

    /**
     * 기업명으로 설립일을 조회합니다.
     * 조회 실패 시 Optional.empty() 반환 (사용자 직접 입력 요청).
     */
    @Retryable(
            retryFor = {ResourceAccessException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 1000)
    )
    public Optional<LocalDate> lookupCompanyFoundingDate(String companyName) {
        try {
            CompanyInfoResponse response = publicDataRestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/company/founding-date")
                            .queryParam("companyName", companyName)
                            .queryParam("serviceKey", apiKey)
                            .build())
                    .retrieve()
                    .toEntity(CompanyInfoResponse.class)
                    .getBody();

            if (response == null || response.foundingDate() == null) {
                log.info("기업 설립일 조회 결과 없음: companyName 생략");
                return Optional.empty();
            }

            return Optional.of(response.foundingDate());

        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                log.info("기업 정보 없음 (4xx), 사용자 직접 입력 필요");
                return Optional.empty();
            }
            log.warn("공공데이터 API 서버 오류 발생, 재시도 예정");
            throw new PublicDataApiException("공공데이터 API 서버 오류", e);
        } catch (ResourceAccessException e) {
            log.warn("공공데이터 API 네트워크 오류 발생, 재시도 예정");
            throw e;
        }
    }

    private record CompanyInfoResponse(LocalDate foundingDate) {}
}
