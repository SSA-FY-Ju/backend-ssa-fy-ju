package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import ssafy.SSAju.dto.external.PublicDataApiResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyInfoService 단위 테스트")
class CompanyInfoServiceTest {

    @Mock
    private RestClient publicDataRestClient;

    private CompanyInfoService companyInfoService;

    // RestClient 체이닝 mock
    @Mock private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    @Mock private RestClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        companyInfoService = new CompanyInfoService(publicDataRestClient);
        ReflectionTestUtils.setField(companyInfoService, "apiKey", "test-api-key");
        // URI.create(publicDataUrl) 호출을 위해 반드시 주입 필요
        ReflectionTestUtils.setField(companyInfoService, "publicDataUrl",
                "https://apis.data.go.kr/1160100/service/GetCorpBasicInfoService_V2");
    }

    @SuppressWarnings("unchecked")
    private void givenApiReturns(PublicDataApiResponse response) {
        given(publicDataRestClient.get()).willReturn((RestClient.RequestHeadersUriSpec) requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(any(java.net.URI.class))).willReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        // 실제 운영 코드는 .retrieve().body(Class) 를 호출하므로 .body() mock
        given(responseSpec.body(PublicDataApiResponse.class)).willReturn(response);
    }

    // ────────────────────────────────────────────────────────────
    // 정상 케이스
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 응답 → 설립일자 LocalDate 반환")
    void shouldReturnFoundingDate_WhenApiReturnsValidData() {
        // Given
        givenApiReturns(buildSuccessResponse("현대오토에버", "20000410"));

        // When
        Optional<LocalDate> result = companyInfoService.lookupCompanyFoundingDate("현대오토에버");

        // Then
        assertThat(result).contains(LocalDate.of(2000, 4, 10));
    }

    @Test
    @DisplayName("결과 2건 이상 → 첫 번째 항목의 설립일자 반환")
    void shouldReturnFirstItem_WhenMultipleResults() {
        // Given: item 배열에 2건 존재
        PublicDataApiResponse.CompanyItem first =
                new PublicDataApiResponse.CompanyItem("crno1", "삼성전자", "19690113", "", "");
        PublicDataApiResponse.CompanyItem second =
                new PublicDataApiResponse.CompanyItem("crno2", "삼성전자 외", "20000101", "", "");

        PublicDataApiResponse response = new PublicDataApiResponse(
                new PublicDataApiResponse.Response(
                        new PublicDataApiResponse.Header("00", "NORMAL SERVICE."),
                        new PublicDataApiResponse.Body(10, 1, 2,
                                new PublicDataApiResponse.Items(List.of(first, second)))
                )
        );
        givenApiReturns(response);

        // When
        Optional<LocalDate> result = companyInfoService.lookupCompanyFoundingDate("삼성전자");

        // Then
        assertThat(result).contains(LocalDate.of(1969, 1, 13));
    }

    // ────────────────────────────────────────────────────────────
    // 빈 결과 케이스
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("검색 결과 0건 → Optional.empty()")
    void shouldReturnEmpty_WhenNoItems() {
        // Given
        PublicDataApiResponse response = new PublicDataApiResponse(
                new PublicDataApiResponse.Response(
                        new PublicDataApiResponse.Header("00", "NORMAL SERVICE."),
                        new PublicDataApiResponse.Body(10, 1, 0,
                                new PublicDataApiResponse.Items(List.of()))
                )
        );
        givenApiReturns(response);

        // When
        Optional<LocalDate> result = companyInfoService.lookupCompanyFoundingDate("없는기업");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("enpEstbDt 빈 문자열 → Optional.empty()")
    void shouldReturnEmpty_WhenFoundingDateBlank() {
        // Given
        givenApiReturns(buildSuccessResponse("테스트기업", ""));

        // When
        Optional<LocalDate> result = companyInfoService.lookupCompanyFoundingDate("테스트기업");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("enpEstbDt 파싱 불가 형식 → Optional.empty()")
    void shouldReturnEmpty_WhenFoundingDateInvalidFormat() {
        // Given: "2017년0" 처럼 파싱 불가 데이터
        givenApiReturns(buildSuccessResponse("테스트기업", "2017년0"));

        // When
        Optional<LocalDate> result = companyInfoService.lookupCompanyFoundingDate("테스트기업");

        // Then
        assertThat(result).isEmpty();
    }

    // ────────────────────────────────────────────────────────────
    // API 에러코드 케이스
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resultCode 10 (잘못된 파라미터) → Optional.empty()")
    void shouldReturnEmpty_WhenResultCode10() {
        // Given
        PublicDataApiResponse response = new PublicDataApiResponse(
                new PublicDataApiResponse.Response(
                        new PublicDataApiResponse.Header("10", "INVALID_REQUEST_PARAMETER_ERROR"),
                        null
                )
        );
        givenApiReturns(response);

        // When
        Optional<LocalDate> result = companyInfoService.lookupCompanyFoundingDate("현대오토에버");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("resultCode 30 (미등록 서비스키) → Optional.empty()")
    void shouldReturnEmpty_WhenResultCode30() {
        // Given
        PublicDataApiResponse response = new PublicDataApiResponse(
                new PublicDataApiResponse.Response(
                        new PublicDataApiResponse.Header("30", "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"),
                        null
                )
        );
        givenApiReturns(response);

        // When
        Optional<LocalDate> result = companyInfoService.lookupCompanyFoundingDate("현대오토에버");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("HTTP 5xx 서버 오류 → 원본 예외 그대로 전파 (재시도 대상, CompanyInfoServiceRetryTest 참고)")
    void shouldRethrowOriginalException_WhenServerError() {
        // Given
        given(publicDataRestClient.get()).willReturn((RestClient.RequestHeadersUriSpec) requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(any(java.net.URI.class))).willReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.body(PublicDataApiResponse.class))
                .willThrow(new org.springframework.web.client.HttpServerErrorException(
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        // When & Then
        assertThatThrownBy(() -> companyInfoService.lookupCompanyFoundingDate("현대오토에버"))
                .isInstanceOf(org.springframework.web.client.HttpServerErrorException.class);
    }

    // ────────────────────────────────────────────────────────────
    // 헬퍼
    // ────────────────────────────────────────────────────────────

    private PublicDataApiResponse buildSuccessResponse(String corpNm, String enpEstbDt) {
        PublicDataApiResponse.CompanyItem item =
                new PublicDataApiResponse.CompanyItem("crno-test", corpNm, enpEstbDt, "", "");
        return new PublicDataApiResponse(
                new PublicDataApiResponse.Response(
                        new PublicDataApiResponse.Header("00", "NORMAL SERVICE."),
                        new PublicDataApiResponse.Body(1, 1, 1,
                                new PublicDataApiResponse.Items(List.of(item)))
                )
        );
    }
}
