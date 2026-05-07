package ssafy.SSAju.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.validator.RequestValidator;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.LocalDate;
import java.time.LocalTime;

@Slf4j
@Service
public class SajuDataService {

    private final RestClient fastApiRestClient;
    private final RequestValidator requestValidator;

    public SajuDataService(@Qualifier("fastApiRestClient") RestClient fastApiRestClient,
                           RequestValidator requestValidator) {
        this.fastApiRestClient = fastApiRestClient;
        this.requestValidator = requestValidator;
    }

    /**
     * FastAPI에서 사주 데이터를 조회합니다.
     *
     * 재시도 정책 (Spring Retry):
     * - ResourceAccessException: 네트워크 오류/타임아웃 → 재시도
     * - HttpServerErrorException: 5xx 서버 오류 → 재시도
     * - 4xx 클라이언트 오류: InvalidSajuDataException으로 변환 → 재시도 안 함
     */
    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public FastAPIResponse fetchSajuFromFastAPI(LocalDate birthDate, LocalTime birthTime) {
        requestValidator.validateBirthInfo(birthDate, birthTime);

        try {
            return fastApiRestClient
                    .post()
                    .uri("/api/saju/calculate")
                    .body(new FastApiRequest(birthDate, birthTime))
                    .retrieve()
                    .toEntity(FastAPIResponse.class)
                    .getBody();
        } catch (ResourceAccessException e) {
            // 네트워크/타임아웃 → @Retryable 재시도 대상
            log.warn("FastAPI 네트워크 오류 발생, 재시도 예정");
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                // 4xx: 입력 오류 → 재시도 안 함
                throw new InvalidSajuDataException(
                        ErrorMessageConstants.FASTAPI_CALL_FAILED.getMessage(), e);
            }
            // 5xx: 서버 오류 → @Retryable 재시도 대상 (원본 예외 유지)
            log.warn("FastAPI 서버 오류 발생, 재시도 예정");
            throw e;
        }
    }

    private record FastApiRequest(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate birthDate,
            @JsonFormat(pattern = "HH:mm") LocalTime birthTime
    ) {}
}
