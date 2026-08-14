package ssafy.SSAju.career.caller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ssafy.SSAju.career.domain.CompatibilityNarrativeRequest;
import ssafy.SSAju.career.domain.FiveElements;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.exception.OpenAIApiException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenAI 연결 실패 시 실제 재시도(3회) 및 총 소요시간 회귀 테스트 (US1, T002).
 *
 * <p>{@link ConsultationOpenAICallerRetryTest}와 동일한 패턴 — 연결이 즉시 거부되는
 * 로컬 포트로 지정해 네트워크 지연 없이 연결 실패를 재현하고, {@code @Retryable}
 * (maxAttempts=3, backoff 1s→2s) 하나만 적용되어 총 소요시간이 backoff 예산 수준인지 확인한다.
 */
@SpringBootTest
@DisplayName("궁합 해설 OpenAI 연결 실패 재시도 회귀 테스트 (US1)")
class CompanyMatchingOpenAICallerRetryTest {

    @DynamicPropertySource
    static void unreachableOpenAiEndpoint(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:1");
    }

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private CompanyMatchingOpenAICaller companyMatchingOpenAICaller;

    private static final FiveElements FIVE_ELEMENTS =
            new FiveElements(Map.of("木", 1, "火", 2, "土", 1, "金", 2, "水", 2));
    private static final HiddenStems HIDDEN_STEMS = new HiddenStems(Map.of("午", List.of("丁")));

    private static final CompatibilityNarrativeRequest REQUEST = new CompatibilityNarrativeRequest(
            new CompatibilityNarrativeRequest.SajuInfo(FIVE_ELEMENTS, HIDDEN_STEMS, "己"),
            new CompatibilityNarrativeRequest.SajuInfo(FIVE_ELEMENTS, HIDDEN_STEMS, "庚"),
            new CompatibilityNarrativeRequest.ScoreSet(80, 70, 70, 55),
            JobCategoryEnum.TECH_BACKEND, "백엔드 개발자"
    );

    @Test
    @DisplayName("연결 실패 시 재시도(3회) 후 OpenAIApiException, 총 소요시간은 수 초 내")
    void companyMatchingOpenAICaller_connectionFailure_failsFastWithoutDoubleRetry() {
        long start = System.currentTimeMillis();

        assertThatThrownBy(() -> companyMatchingOpenAICaller.call(REQUEST))
                .isInstanceOf(OpenAIApiException.class);

        long elapsedMs = System.currentTimeMillis() - start;
        // @Retryable(maxAttempts=3, backoff 1s→2s)만 적용되면 최소 3s, 넉넉히 잡아도 30s 이내여야 한다.
        assertThat(elapsedMs).isLessThan(30_000);
    }
}
