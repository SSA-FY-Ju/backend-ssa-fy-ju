package ssafy.SSAju.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.exception.FastAPITimeoutException;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class SajuDataService {

    private final WebClient fastApiWebClient;
    private final int maxRetries;

    public SajuDataService(
            @Qualifier("fastApiWebClient") WebClient fastApiWebClient,
            @Value("${saju.fastapi.max-retries:2}") int maxRetries
    ) {
        this.fastApiWebClient = fastApiWebClient;
        this.maxRetries = maxRetries;
    }

    public FastAPIResponse fetchSajuFromFastAPI(LocalDate birthDate, LocalTime birthTime) {
        if (birthDate == null) {
            throw new InvalidSajuDataException("생년월일이 필수입니다");
        }
        if (birthTime == null) {
            throw new InvalidSajuDataException("태어난 시간이 필수입니다 (HH:mm 형식)");
        }

        String birthDateTime = birthDate.format(DateTimeFormatter.ISO_DATE)
                + " " + birthTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        log.info("FastAPI 사주 조회 시작: {}", birthDateTime);

        return executeWithRetry(birthDateTime, 0);
    }

    private FastAPIResponse executeWithRetry(String birthDateTime, int attempt) {
        try {
            return fastApiWebClient.post()
                    .uri("/saju/calculate")
                    .bodyValue(new FastApiRequest(birthDateTime))
                    .retrieve()
                    .bodyToMono(FastAPIResponse.class)
                    .block();
        } catch (Exception e) {
            if (isTimeout(e) && attempt < maxRetries) {
                long delayMs = (long) Math.pow(2, attempt) * 1000;
                log.warn("FastAPI 타임아웃 (시도 {}/{}), {}ms 후 재시도", attempt + 1, maxRetries + 1, delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new FastAPITimeoutException("FastAPI 호출 중 스레드 중단됨", ie);
                }
                return executeWithRetry(birthDateTime, attempt + 1);
            }

            if (isTimeout(e)) {
                throw new FastAPITimeoutException(
                        "FastAPI 요청 시간 초과 (" + (maxRetries + 1) + "회 시도)", e);
            }
            log.error("FastAPI 호출 실패: {}", birthDateTime, e);
            throw new FastAPITimeoutException("FastAPI 호출 실패", e);
        }
    }

    private boolean isTimeout(Exception e) {
        if (e.getCause() instanceof java.util.concurrent.TimeoutException) return true;
        String msg = e.getMessage();
        return msg != null && (msg.contains("timeout") || msg.contains("Timeout") || msg.contains("timed out"));
    }

    private record FastApiRequest(String birthDateTime) {
    }
}
