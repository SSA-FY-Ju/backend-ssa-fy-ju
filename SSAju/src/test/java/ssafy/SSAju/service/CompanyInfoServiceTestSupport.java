package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * {@link CompanyInfoService} 단위 테스트 공통 Mock/셋업.
 * RestClient 체이닝 mock과 서비스 인스턴스 생성 로직을 공유해
 * 여러 테스트 클래스(정상/에러코드 케이스, 재시도 정책 케이스)에서 중복 작성하지 않도록 한다.
 */
@ExtendWith(MockitoExtension.class)
abstract class CompanyInfoServiceTestSupport {

    @Mock
    protected RestClient publicDataRestClient;

    @Mock protected RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    @Mock protected RestClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock protected RestClient.ResponseSpec responseSpec;

    protected CompanyInfoService companyInfoService;

    @BeforeEach
    void setUpCompanyInfoService() {
        companyInfoService = new CompanyInfoService(publicDataRestClient);
        ReflectionTestUtils.setField(companyInfoService, "apiKey", "test-api-key");
        // URI.create(publicDataUrl) 호출을 위해 반드시 주입 필요
        ReflectionTestUtils.setField(companyInfoService, "publicDataUrl",
                "https://apis.data.go.kr/1160100/service/GetCorpBasicInfoService_V2");
    }

    /** {@code publicDataRestClient.get().uri(...).retrieve()}까지의 체이닝을 스텁한다. */
    @SuppressWarnings("unchecked")
    protected void stubRetrieve() {
        given(publicDataRestClient.get()).willReturn((RestClient.RequestHeadersUriSpec) requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(any(URI.class))).willReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
    }
}
