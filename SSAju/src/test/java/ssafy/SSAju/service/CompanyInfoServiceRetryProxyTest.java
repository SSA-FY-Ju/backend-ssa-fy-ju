package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import ssafy.SSAju.dto.external.PublicDataApiResponse;
import ssafy.SSAju.exception.ExternalApiException;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link CompanyInfoService}의 {@code @Retryable}/{@code @Recover}가 실제 Spring AOP 프록시를
 * 통해 동작하는지 검증한다 (US3, T025 보강).
 * <p>
 * {@link CompanyInfoServiceRetryTest}(단위 테스트)는 {@code new CompanyInfoService(...)}로
 * 직접 생성한 인스턴스를 호출하므로 재시도/복구 어노테이션이 적용된 프록시를 거치지 않아
 * 원본 예외 재전파(1회 호출)까지만 검증할 수 있다. 여기서는 Spring 컨텍스트가 관리하는
 * 실제 빈을 주입받아 재시도 횟수와 재시도 소진 후 최종 복구 예외까지 검증한다.
 */
@SpringBootTest
@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyInfoService 재시도 프록시 통합 테스트 (US3)")
class CompanyInfoServiceRetryProxyTest {

    private static final String CORP_NAME = "현대오토에버";

    @MockitoBean(name = "publicDataRestClient")
    private RestClient publicDataRestClient;

    @Mock private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    @Mock private RestClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    @Autowired
    private CompanyInfoService companyInfoService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        given(publicDataRestClient.get()).willReturn((RestClient.RequestHeadersUriSpec) requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(any(URI.class))).willReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
    }

    private PublicDataApiResponse successResponse() {
        PublicDataApiResponse.CompanyItem item =
                new PublicDataApiResponse.CompanyItem("crno-test", CORP_NAME, "20000410", "", "");
        return new PublicDataApiResponse(
                new PublicDataApiResponse.Response(
                        new PublicDataApiResponse.Header("00", "NORMAL SERVICE."),
                        new PublicDataApiResponse.Body(1, 1, 1,
                                new PublicDataApiResponse.Items(List.of(item)))
                )
        );
    }

    @Test
    @DisplayName("5xx 1회 실패 후 재시도 성공 → 총 2회 호출, 정상 응답 반환")
    void retriesOnceThenSucceeds_WhenFirstAttemptIsServerError() {
        // Given: 1차 호출은 5xx, 2차 호출은 성공
        HttpServerErrorException serverError = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", null, null, null);
        given(responseSpec.body(PublicDataApiResponse.class))
                .willThrow(serverError)
                .willReturn(successResponse());

        // When
        Optional<LocalDate> result = companyInfoService.lookupCompanyFoundingDate(CORP_NAME);

        // Then
        assertThat(result).contains(LocalDate.of(2000, 4, 10));
        verify(responseSpec, times(2)).body(PublicDataApiResponse.class);
    }

    @Test
    @DisplayName("5xx 2회 연속 실패 → 재시도 소진 후 ExternalApiException으로 복구")
    void recoversToExternalApiException_WhenServerErrorPersists() {
        // Given: 두 번의 호출 모두 5xx
        HttpServerErrorException serverError = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", null, null, null);
        given(responseSpec.body(PublicDataApiResponse.class)).willThrow(serverError);

        // When & Then
        assertThatThrownBy(() -> companyInfoService.lookupCompanyFoundingDate(CORP_NAME))
                .isInstanceOf(ExternalApiException.class);
        verify(responseSpec, times(2)).body(PublicDataApiResponse.class);
    }

    @Test
    @DisplayName("네트워크 오류 2회 연속 실패 → 재시도 소진 후 ExternalApiException으로 복구")
    void recoversToExternalApiException_WhenNetworkErrorPersists() {
        // Given: 두 번의 호출 모두 네트워크 오류
        given(responseSpec.body(PublicDataApiResponse.class))
                .willThrow(new ResourceAccessException("Connection refused"));

        // When & Then
        assertThatThrownBy(() -> companyInfoService.lookupCompanyFoundingDate(CORP_NAME))
                .isInstanceOf(ExternalApiException.class);
        verify(responseSpec, times(2)).body(PublicDataApiResponse.class);
    }

    @Test
    @DisplayName("4xx 응답 → 재시도 없이 1회만 호출")
    void doesNotRetry_WhenClientError() {
        // Given
        given(responseSpec.body(PublicDataApiResponse.class))
                .willThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

        // When
        Optional<LocalDate> result = companyInfoService.lookupCompanyFoundingDate(CORP_NAME);

        // Then
        assertThat(result).isEmpty();
        verify(responseSpec, times(1)).body(PublicDataApiResponse.class);
    }
}
