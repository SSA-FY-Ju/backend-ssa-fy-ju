package ssafy.SSAju.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import ssafy.SSAju.dto.external.PublicDataApiResponse;
import ssafy.SSAju.exception.ExternalApiException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * 금융위원회 공공데이터 Open API를 통해 기업 설립일을 조회합니다.
 * <p>
 * API: getCorpOutline_V2 (기업개요조회)
 * URL: /getCorpOutline_V2
 * 검색: corpNm (법인명) 필수 사용
 * <p>
 * 조회 결과가 없거나 4xx 오류 시 {@code Optional.empty()} 반환 →
 * 호출자가 {@code PublicDataApiException}으로 처리하거나 사용자 직접 입력 요청.
 * 5xx/네트워크 오류는 원본 예외를 그대로 전파해 {@code @Retryable}이 재시도하며,
 * 재시도 소진 시 {@code @Recover}가 {@link ExternalApiException}으로 변환해 던진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyInfoService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Qualifier("publicDataRestClient")
    private final RestClient publicDataRestClient;

    @Value("${saju.public-data.api-key}")
    private String apiKey;

    @Value("${saju.public-data.url}")
    private String publicDataUrl;

    /**
     * 기업명(법인명)으로 설립일자를 조회합니다.
     * <p>
     * 결과가 여러 건이면 첫 번째 항목의 설립일자를 사용합니다.
     *
     * @param corpNm 법인명 (예: "현대오토에버")
     * @return 설립일자 Optional, 조회 실패/결과 없음/설립일 미제공 시 empty
     */
    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 1000)
    )
    public Optional<LocalDate> lookupCompanyFoundingDate(String corpNm) {
        try {
            String encodedApiKey = apiKey;
            // 공공데이터 API의 corpNm 파라미터: RFC 3986 기반 인코딩 (공백→%20)
            // .build(true)와 함께 사용해 중복 인코딩 방지
            String encodedCorpNm = UriUtils.encodeQueryParam(corpNm, StandardCharsets.UTF_8);

            URI uri = UriComponentsBuilder
                    .fromUri(URI.create(publicDataUrl))
                    .path("/getCorpOutline_V2")
                    .queryParam("ServiceKey", encodedApiKey)
                    .queryParam("corpNm", encodedCorpNm)
                    .queryParam("pageNo", 1)
                    .queryParam("numOfRows", 1)
                    .queryParam("resultType", "json")
                    .build(true) // 핵심: "파라미터가 이미 인코딩됨"을 선언하여 중복 인코딩 방지
                    .toUri();

            PublicDataApiResponse response = publicDataRestClient
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(PublicDataApiResponse.class);

            return extractFoundingDate(response, corpNm);

        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                log.info("공공데이터 API 클라이언트 오류 ({}): corpNm 생략", e.getStatusCode().value());
                return Optional.empty();
            }
            // 5xx는 @Retryable 대상이므로 원본 예외 그대로 재전파
            log.warn("공공데이터 API 서버 오류 ({}), 재시도 예정", e.getStatusCode().value());
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("공공데이터 API 네트워크 오류 발생, 재시도 예정: {}", e.getMessage());
            throw e;
        }
    }

    @Recover
    public Optional<LocalDate> recoverFromServerError(HttpServerErrorException ex, String corpNm) {
        log.error("공공데이터 API 서버 오류: 재시도 후 최종 실패", ex);
        throw new ExternalApiException("공공데이터 API 서버 오류", ex);
    }

    @Recover
    public Optional<LocalDate> recoverFromNetworkError(ResourceAccessException ex, String corpNm) {
        log.error("공공데이터 API 네트워크 오류: 재시도 후 최종 실패", ex);
        throw new ExternalApiException("공공데이터 API 네트워크 오류", ex);
    }

    /**
     * API 응답에서 설립일자({@code enpEstbDt})를 추출하여 {@link LocalDate}로 변환합니다.
     */
    private Optional<LocalDate> extractFoundingDate(PublicDataApiResponse response, String corpNm) {
        if (response == null || response.response() == null) {
            log.info("공공데이터 API 응답 없음: corpNm 생략");
            return Optional.empty();
        }

        PublicDataApiResponse.Header header = response.response().header();
        if (header == null || !header.isSuccess()) {
            String code = header != null ? header.resultCode() : "null";
            log.info("공공데이터 API 에러코드 {}: corpNm 생략", code);
            return Optional.empty();
        }

        PublicDataApiResponse.Body body = response.response().body();
        if (body == null || body.items() == null) {
            log.info("공공데이터 API 응답 body 없음");
            return Optional.empty();
        }

        List<PublicDataApiResponse.CompanyItem> items = body.items().item();
        if (items == null || items.isEmpty()) {
            log.info("기업 검색 결과 없음: corpNm 생략");
            return Optional.empty();
        }

        String enpEstbDt = items.get(0).enpEstbDt();
        if (enpEstbDt == null || enpEstbDt.isBlank()) {
            log.info("설립일자 데이터 없음: corpNm 생략");
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDate.parse(enpEstbDt, YYYYMMDD));
        } catch (DateTimeParseException e) {
            log.warn("설립일자 파싱 실패 (값: {}): {}", enpEstbDt, e.getMessage());
            return Optional.empty();
        }
    }
}
