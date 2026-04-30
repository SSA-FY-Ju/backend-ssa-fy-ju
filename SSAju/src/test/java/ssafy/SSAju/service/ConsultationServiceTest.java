package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.request.ConsultationRequest;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.exception.OpenAIApiException;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserProfileRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultationService 단위 테스트")
class ConsultationServiceTest {

    @Mock private ChatClient chatClient;
    @Mock private SajuDataService sajuDataService;
    @Mock private TenGodCalculator tenGodCalculator;
    @Mock private HiddenStemCalculator hiddenStemCalculator;
    @Mock private CareerFortuneAnalyzer careerFortuneAnalyzer;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private SajuResultRepository sajuResultRepository;
    @Mock private CareerConsultationRepository careerConsultationRepository;

    private ConsultationService service;

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 10, 10);
    private static final LocalTime BIRTH_TIME = LocalTime.of(14, 30);

    private static final ConsultationRequest VALID_REQUEST = new ConsultationRequest(BIRTH_DATE, BIRTH_TIME);

    private static final FastAPIResponse MOCK_SAJU = new FastAPIResponse(
            List.of("庚", "甲", "己", "丁"),
            List.of("午", "戌", "未", "寅"),
            Map.of("木", 1, "火", 2, "土", 2, "金", 2, "水", 1),
            "庚午", "甲戌", "己未", "丁寅",
            "14:30", "1990-10-10", Map.of()
    );

    private static final Map<String, Integer> TEN_GOD = Map.of("정관", 1, "편관", 1);
    private static final Map<String, List<String>> HIDDEN_STEMS =
            Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                   "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊"));

    private static final CareerAdviceResponse MOCK_ADVICE = new CareerAdviceResponse(
            List.of(Map.of("name", "금융/핀테크", "reason", "오행 金 강세로 재무 분야 적합"),
                    Map.of("name", "IT/소프트웨어", "reason", "논리력 강함")),
            List.of("일관성 있는 자기소개 준비", "데이터 기반 성과 사례 강조"),
            List.of("분석력과 논리성", "책임감 있는 업무 추진")
    );

    @BeforeEach
    void setUp() {
        service = new ConsultationService(
                chatClient, sajuDataService, tenGodCalculator, hiddenStemCalculator, careerFortuneAnalyzer,
                userProfileRepository, sajuResultRepository, careerConsultationRepository);
        try {
            var field = ConsultationService.class.getDeclaredField("modelVersion");
            field.setAccessible(true);
            field.set(service, "gpt-4o-mini");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ─────────────────────────────────────────
    // 정상 플로우 — SajuResult 기존 존재
    // ─────────────────────────────────────────

    @Test
    @DisplayName("유효한 요청 + SajuResult 존재 → 컨설팅 결과 반환 및 DB 저장")
    void shouldReturnConsultation_WhenSajuResultExists() {
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(tenGodCalculator.calculate(MOCK_SAJU.heavenlyStems())).willReturn(TEN_GOD);
        given(hiddenStemCalculator.calculate(MOCK_SAJU.earthlyBranches())).willReturn(HIDDEN_STEMS);
        given(careerFortuneAnalyzer.analyzeFavoredPeriod(any(), any(), any(), any())).willReturn("H1");
        given(careerFortuneAnalyzer.calculateConfidenceScore(any(), any(), any())).willReturn(80);
        given(userProfileRepository.findByBirthDateAndBirthTime(BIRTH_DATE, BIRTH_TIME))
                .willReturn(Optional.of(userProfile));
        given(sajuResultRepository.findByUserProfile(userProfile)).willReturn(Optional.of(sajuResult));
        given(careerConsultationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        var promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        var callSpec = mock(ChatClient.CallResponseSpec.class);
        given(chatClient.prompt()).willReturn(promptSpec);
        given(promptSpec.user(any(String.class))).willReturn(promptSpec);
        given(promptSpec.call()).willReturn(callSpec);
        given(callSpec.entity(CareerAdviceResponse.class)).willReturn(MOCK_ADVICE);

        ConsultationResponse result = service.getCareerConsultation(VALID_REQUEST);

        assertThat(result.industries()).hasSize(2);
        assertThat(result.interviewTips()).hasSize(2);
        assertThat(result.strengths()).hasSize(2);
        assertThat(result.openaiModelVersion()).isEqualTo("gpt-4o-mini");
        assertThat(result.favoredPeriod()).isEqualTo("H1");
        assertThat(result.confidenceScore()).isEqualTo(80);
        assertThat(result.reasoning()).isNotBlank();
        verify(careerConsultationRepository).save(any());
    }

    // ─────────────────────────────────────────
    // 정상 플로우 — SajuResult 신규 생성
    // ─────────────────────────────────────────

    @Test
    @DisplayName("유효한 요청 + SajuResult 없음 → 신규 SajuResult 생성 후 컨설팅 반환")
    void shouldReturnConsultation_WhenSajuResultCreated() {
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(tenGodCalculator.calculate(MOCK_SAJU.heavenlyStems())).willReturn(TEN_GOD);
        given(hiddenStemCalculator.calculate(MOCK_SAJU.earthlyBranches())).willReturn(HIDDEN_STEMS);
        given(careerFortuneAnalyzer.analyzeFavoredPeriod(any(), any(), any(), any())).willReturn("H2");
        given(careerFortuneAnalyzer.calculateConfidenceScore(any(), any(), any())).willReturn(60);
        given(userProfileRepository.findByBirthDateAndBirthTime(BIRTH_DATE, BIRTH_TIME))
                .willReturn(Optional.of(userProfile));
        given(sajuResultRepository.findByUserProfile(userProfile)).willReturn(Optional.empty());
        given(sajuResultRepository.save(any())).willReturn(sajuResult);
        given(careerConsultationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        var promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        var callSpec = mock(ChatClient.CallResponseSpec.class);
        given(chatClient.prompt()).willReturn(promptSpec);
        given(promptSpec.user(any(String.class))).willReturn(promptSpec);
        given(promptSpec.call()).willReturn(callSpec);
        given(callSpec.entity(CareerAdviceResponse.class)).willReturn(MOCK_ADVICE);

        ConsultationResponse result = service.getCareerConsultation(VALID_REQUEST);

        assertThat(result.openaiModelVersion()).isEqualTo("gpt-4o-mini");
        verify(sajuResultRepository).save(any());
        verify(careerConsultationRepository).save(any());
    }

    // ─────────────────────────────────────────
    // OpenAI 호출 실패
    // ─────────────────────────────────────────

    @Test
    @DisplayName("OpenAI API 호출 실패 → OpenAIApiException")
    void shouldThrow_WhenOpenAIFails() {
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(tenGodCalculator.calculate(any())).willReturn(TEN_GOD);
        given(hiddenStemCalculator.calculate(any())).willReturn(HIDDEN_STEMS);
        given(careerFortuneAnalyzer.analyzeFavoredPeriod(any(), any(), any(), any())).willReturn("H1");
        given(careerFortuneAnalyzer.calculateConfidenceScore(any(), any(), any())).willReturn(70);
        given(userProfileRepository.findByBirthDateAndBirthTime(BIRTH_DATE, BIRTH_TIME))
                .willReturn(Optional.of(userProfile));
        given(sajuResultRepository.findByUserProfile(userProfile)).willReturn(Optional.of(sajuResult));

        var promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        var callSpec = mock(ChatClient.CallResponseSpec.class);
        given(chatClient.prompt()).willReturn(promptSpec);
        given(promptSpec.user(any(String.class))).willReturn(promptSpec);
        given(promptSpec.call()).willReturn(callSpec);
        given(callSpec.entity(CareerAdviceResponse.class))
                .willThrow(new RuntimeException("OpenAI connection failed"));

        assertThatThrownBy(() -> service.getCareerConsultation(VALID_REQUEST))
                .isInstanceOf(OpenAIApiException.class)
                .hasMessageContaining("OpenAI API 호출 실패");
    }

    // ─────────────────────────────────────────
    // OpenAI 응답 null
    // ─────────────────────────────────────────

    @Test
    @DisplayName("OpenAI 응답 null → OpenAIApiException")
    void shouldThrow_WhenOpenAIReturnsNull() {
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(tenGodCalculator.calculate(any())).willReturn(TEN_GOD);
        given(hiddenStemCalculator.calculate(any())).willReturn(HIDDEN_STEMS);
        given(careerFortuneAnalyzer.analyzeFavoredPeriod(any(), any(), any(), any())).willReturn("H1");
        given(careerFortuneAnalyzer.calculateConfidenceScore(any(), any(), any())).willReturn(70);
        given(userProfileRepository.findByBirthDateAndBirthTime(BIRTH_DATE, BIRTH_TIME))
                .willReturn(Optional.of(userProfile));
        given(sajuResultRepository.findByUserProfile(userProfile)).willReturn(Optional.of(sajuResult));

        var promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        var callSpec = mock(ChatClient.CallResponseSpec.class);
        given(chatClient.prompt()).willReturn(promptSpec);
        given(promptSpec.user(any(String.class))).willReturn(promptSpec);
        given(promptSpec.call()).willReturn(callSpec);
        given(callSpec.entity(CareerAdviceResponse.class)).willReturn(null);

        assertThatThrownBy(() -> service.getCareerConsultation(VALID_REQUEST))
                .isInstanceOf(OpenAIApiException.class)
                .hasMessageContaining("비어있습니다");
    }
}
