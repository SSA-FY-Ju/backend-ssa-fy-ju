package ssafy.SSAju.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import ssafy.SSAju.dto.external.PublicDataApiResponse;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * US3(공공데이터 API 일시 장애 자동 재시도) 검증:
 * 5xx는 원본 예외 그대로 전파되어 {@code @Retryable}이 재시도할 수 있어야 하고,
 * 4xx는 즉시 빈 결과로 처리되어 재시도하지 않아야 한다.
 */
@DisplayName("CompanyInfoService 재시도 정책 단위 테스트")
class CompanyInfoServiceRetryTest extends CompanyInfoServiceTestSupport {

    private void givenRestClientThrows(RuntimeException exception) {
        stubRetrieve();
        given(responseSpec.body(PublicDataApiResponse.class)).willThrow(exception);
    }

    @Test
    @DisplayName("5xx 응답 → 원본 HttpServerErrorException 그대로 전파 (재시도 트리거)")
    void shouldRethrowOriginalException_WhenServerError() {
        // Given
        HttpServerErrorException ex = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", null, null, null);
        givenRestClientThrows(ex);

        // When & Then
        assertThatThrownBy(() -> companyInfoService.lookupCompanyFoundingDate("현대오토에버"))
                .isInstanceOf(HttpServerErrorException.class)
                .satisfies(e -> assertThat(((HttpServerErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    @DisplayName("4xx 응답 → 즉시 Optional.empty() (재시도 없음)")
    void shouldReturnEmpty_WhenClientError() {
        // Given
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null, null, null);
        givenRestClientThrows(ex);

        // When
        Optional<LocalDate> result = companyInfoService.lookupCompanyFoundingDate("현대오토에버");

        // Then
        assertThat(result).isEmpty();
    }
}
