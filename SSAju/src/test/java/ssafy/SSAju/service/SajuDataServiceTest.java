package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ssafy.SSAju.career.validator.RequestValidator;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SajuDataService 단위 테스트")
class SajuDataServiceTest {

    @Mock private RestClient fastApiRestClient;
    @Mock private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock(answer = Answers.RETURNS_SELF) private RestClient.RequestBodySpec requestBodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private SajuDataService service;

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 10, 10);
    private static final LocalTime BIRTH_TIME = LocalTime.of(14, 30);

    private static final FastAPIResponse MOCK_RESPONSE = new FastAPIResponse(
            List.of("庚", "甲", "己", "丁"),
            List.of("午", "戌", "未", "寅"),
            Map.of("木", 1, "火", 2, "土", 2, "金", 2, "水", 1),
            "庚午", "甲戌", "己未", "丁寅",
            "14:30", "1990-10-10", Map.of()
    );

    @BeforeEach
    void setUp() {
        service = new SajuDataService(fastApiRestClient, new RequestValidator());
    }

    private void setupRestClientChain() {
        given(fastApiRestClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
        // body()는 RETURNS_SELF로 자동 처리 (overload 매칭 문제 회피)
        given(requestBodySpec.retrieve()).willReturn(responseSpec);
    }

    // ─────────────────────────────────────────
    // 정상 플로우
    // ─────────────────────────────────────────

    @Test
    @DisplayName("유효한 요청 → FastAPI 정상 응답 반환")
    void shouldReturnFastAPIResponse_WhenValidRequest() {
        setupRestClientChain();
        given(responseSpec.toEntity(FastAPIResponse.class))
                .willReturn(ResponseEntity.ok(MOCK_RESPONSE));

        FastAPIResponse result = service.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME);

        assertThat(result).isNotNull();
        assertThat(result.heavenlyStems()).hasSize(4);
        assertThat(result.earthlyBranches()).hasSize(4);
        assertThat(result.fiveElements()).containsKey("木");
        assertThat(result.dayPillar()).isEqualTo("己未");
    }

    // ─────────────────────────────────────────
    // 입력 유효성 검사
    // ─────────────────────────────────────────

    @Test
    @DisplayName("생년월일 null → InvalidSajuDataException")
    void shouldThrow_WhenBirthDateNull() {
        assertThatThrownBy(() -> service.fetchSajuFromFastAPI(null, BIRTH_TIME))
                .isInstanceOf(InvalidSajuDataException.class);
    }

    @Test
    @DisplayName("태어난 시간 null → InvalidSajuDataException")
    void shouldThrow_WhenBirthTimeNull() {
        assertThatThrownBy(() -> service.fetchSajuFromFastAPI(BIRTH_DATE, null))
                .isInstanceOf(InvalidSajuDataException.class);
    }

    // ─────────────────────────────────────────
    // 예외 처리 — 4xx (재시도 없음)
    // ─────────────────────────────────────────

    @Test
    @DisplayName("FastAPI 4xx 응답 → InvalidSajuDataException (재시도 없음)")
    void shouldThrow_InvalidSajuDataException_WhenFastAPIReturns4xx() {
        setupRestClientChain();
        HttpClientErrorException ex = HttpClientErrorException.create(
                "Bad Request", HttpStatus.BAD_REQUEST, "Bad Request",
                null, null, null);
        given(responseSpec.toEntity(FastAPIResponse.class)).willThrow(ex);

        assertThatThrownBy(() -> service.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME))
                .isInstanceOf(InvalidSajuDataException.class);
    }

    // ─────────────────────────────────────────
    // 예외 처리 — 5xx (재시도 대상)
    // ─────────────────────────────────────────

    @Test
    @DisplayName("FastAPI 5xx 응답 → RestClientResponseException 재전파 (재시도 트리거)")
    void shouldRethrow_RestClientResponseException_WhenFastAPIReturns5xx() {
        setupRestClientChain();
        HttpServerErrorException ex = HttpServerErrorException.create(
                "Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                null, null, null);
        given(responseSpec.toEntity(FastAPIResponse.class)).willThrow(ex);

        assertThatThrownBy(() -> service.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME))
                .isInstanceOf(RestClientResponseException.class)
                .satisfies(e -> assertThat(((RestClientResponseException) e).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    // ─────────────────────────────────────────
    // 예외 처리 — 네트워크 오류 (재시도 대상)
    // ─────────────────────────────────────────

    @Test
    @DisplayName("FastAPI 네트워크 오류 → ResourceAccessException 재전파 (재시도 트리거)")
    void shouldRethrow_ResourceAccessException_WhenNetworkError() {
        setupRestClientChain();
        given(responseSpec.toEntity(FastAPIResponse.class))
                .willThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> service.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME))
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("Connection refused");
    }
}
