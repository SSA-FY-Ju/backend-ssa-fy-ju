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
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.request.ConsultationRequest;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;
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
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private SajuResultRepository sajuResultRepository;
    @Mock private CareerConsultationRepository careerConsultationRepository;

    private ConsultationService service;

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 10, 10);
    private static final LocalTime BIRTH_TIME = LocalTime.of(14, 30);

    private static final ConsultationRequest VALID_REQUEST = new ConsultationRequest(
            BIRTH_DATE,
            BIRTH_TIME,
            List.of("庚", "甲", "己", "丁"),
            List.of("午", "戌", "未", "寅"),
            Map.of("木", 1, "火", 2, "土", 2, "金", 2, "水", 1),
            Map.of("午", List.of("丁", "己"), "戌", List.of("戊", "辛", "丁")),
            Map.of("정관", 1, "편관", 1)
    );

    private static final CareerAdviceResponse MOCK_ADVICE = new CareerAdviceResponse(
            List.of(
                    Map.of("name", "금융/핀테크", "reason", "오행 金 강세로 재무 분야 적합"),
                    Map.of("name", "IT/소프트웨어", "reason", "논리력 강함")
            ),
            List.of("일관성 있는 자기소개 준비", "데이터 기반 성과 사례 강조"),
            List.of("분석력과 논리성", "책임감 있는 업무 추진")
    );

    @BeforeEach
    void setUp() {
        service = new ConsultationService(
                chatClient, userProfileRepository, sajuResultRepository, careerConsultationRepository);
        // modelVersion 필드는 @Value로 주입되므로 리플렉션으로 직접 설정
        try {
            var field = ConsultationService.class.getDeclaredField("modelVersion");
            field.setAccessible(true);
            field.set(service, "gpt-4o-mini");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ─────────────────────────────────────────
    // 정상 플로우
    // ─────────────────────────────────────────

    @Test
    @DisplayName("유효한 요청 → 컨설팅 결과 반환 및 DB 저장")
    void shouldReturnConsultation_WhenValidRequest() {
        // Given
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);

        given(userProfileRepository.findByBirthDateAndBirthTime(BIRTH_DATE, BIRTH_TIME))
                .willReturn(Optional.of(userProfile));
        given(sajuResultRepository.findByUserProfile(userProfile))
                .willReturn(Optional.of(sajuResult));

        // ChatClient 체인 모킹
        var promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        var callSpec = mock(ChatClient.CallResponseSpec.class);
        given(chatClient.prompt()).willReturn(promptSpec);
        given(promptSpec.user(any(String.class))).willReturn(promptSpec);
        given(promptSpec.call()).willReturn(callSpec);
        given(callSpec.entity(CareerAdviceResponse.class)).willReturn(MOCK_ADVICE);

        given(careerConsultationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        ConsultationResponse result = service.getCareerConsultation(VALID_REQUEST);

        // Then
        assertThat(result.industries()).hasSize(2);
        assertThat(result.interviewTips()).hasSize(2);
        assertThat(result.strengths()).hasSize(2);
        assertThat(result.openaiModelVersion()).isEqualTo("gpt-4o-mini");
        verify(careerConsultationRepository).save(any());
    }

    // ─────────────────────────────────────────
    // UserProfile 없을 때
    // ─────────────────────────────────────────

    @Test
    @DisplayName("관운 분석 이력 없음 → InvalidSajuDataException")
    void shouldThrow_WhenUserProfileNotFound() {
        // Given
        given(userProfileRepository.findByBirthDateAndBirthTime(BIRTH_DATE, BIRTH_TIME))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.getCareerConsultation(VALID_REQUEST))
                .isInstanceOf(InvalidSajuDataException.class)
                .hasMessageContaining("관운 분석");
    }

    // ─────────────────────────────────────────
    // SajuResult 없을 때
    // ─────────────────────────────────────────

    @Test
    @DisplayName("SajuResult 없음 → InvalidSajuDataException")
    void shouldThrow_WhenSajuResultNotFound() {
        // Given
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        given(userProfileRepository.findByBirthDateAndBirthTime(BIRTH_DATE, BIRTH_TIME))
                .willReturn(Optional.of(userProfile));
        given(sajuResultRepository.findByUserProfile(userProfile))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.getCareerConsultation(VALID_REQUEST))
                .isInstanceOf(InvalidSajuDataException.class)
                .hasMessageContaining("관운 분석");
    }

    // ─────────────────────────────────────────
    // OpenAI 호출 실패
    // ─────────────────────────────────────────

    @Test
    @DisplayName("OpenAI API 호출 실패 → OpenAIApiException")
    void shouldThrow_WhenOpenAIFails() {
        // Given
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);

        given(userProfileRepository.findByBirthDateAndBirthTime(BIRTH_DATE, BIRTH_TIME))
                .willReturn(Optional.of(userProfile));
        given(sajuResultRepository.findByUserProfile(userProfile))
                .willReturn(Optional.of(sajuResult));

        var promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        var callSpec = mock(ChatClient.CallResponseSpec.class);
        given(chatClient.prompt()).willReturn(promptSpec);
        given(promptSpec.user(any(String.class))).willReturn(promptSpec);
        given(promptSpec.call()).willReturn(callSpec);
        given(callSpec.entity(CareerAdviceResponse.class))
                .willThrow(new RuntimeException("OpenAI connection failed"));

        // When & Then
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
        // Given
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);

        given(userProfileRepository.findByBirthDateAndBirthTime(BIRTH_DATE, BIRTH_TIME))
                .willReturn(Optional.of(userProfile));
        given(sajuResultRepository.findByUserProfile(userProfile))
                .willReturn(Optional.of(sajuResult));

        var promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        var callSpec = mock(ChatClient.CallResponseSpec.class);
        given(chatClient.prompt()).willReturn(promptSpec);
        given(promptSpec.user(any(String.class))).willReturn(promptSpec);
        given(promptSpec.call()).willReturn(callSpec);
        given(callSpec.entity(CareerAdviceResponse.class)).willReturn(null);

        // When & Then
        assertThatThrownBy(() -> service.getCareerConsultation(VALID_REQUEST))
                .isInstanceOf(OpenAIApiException.class)
                .hasMessageContaining("비어있습니다");
    }
}
