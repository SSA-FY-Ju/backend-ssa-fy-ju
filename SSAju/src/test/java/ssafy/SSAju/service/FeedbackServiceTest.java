package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.career.enums.FeedbackType;
import ssafy.SSAju.career.enums.SatisfactionStatus;
import ssafy.SSAju.dto.request.SatisfactionFeedbackRequest;
import ssafy.SSAju.dto.response.SatisfactionFeedbackResponse;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserSatisfactionFeedbackRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackService 단위 테스트")
class FeedbackServiceTest {

    @Mock private SajuResultRepository sajuResultRepository;
    @Mock private UserSatisfactionFeedbackRepository feedbackRepository;

    private FeedbackService service;

    private static final UserProfile PROFILE = UserProfile.builder()
            .birthDate(LocalDate.of(1990, 10, 10))
            .birthTime(LocalTime.of(14, 30))
            .build();

    private static final SajuResult SAJU_RESULT = SajuResult.builder()
            .userProfile(PROFILE)
            .build();

    @BeforeEach
    void setUp() {
        service = new FeedbackService(sajuResultRepository, feedbackRepository);
    }

    @Test
    @DisplayName("유효한 피드백 요청 → 피드백 저장 후 응답 반환")
    void shouldSaveFeedback_WhenValidRequest() {
        // Given
        var request = new SatisfactionFeedbackRequest(
                1L, FeedbackType.CAREER_TIMING, SatisfactionStatus.SATISFIED, "좋았습니다");

        var savedFeedback = UserSatisfactionFeedback.builder()
                .sajuResult(SAJU_RESULT)
                .feedbackType(FeedbackType.CAREER_TIMING)
                .satisfactionStatus(SatisfactionStatus.SATISFIED)
                .feedbackContent("좋았습니다")
                .build();

        given(sajuResultRepository.findById(1L)).willReturn(Optional.of(SAJU_RESULT));
        given(feedbackRepository.save(any(UserSatisfactionFeedback.class))).willReturn(savedFeedback);

        // When
        SatisfactionFeedbackResponse response = service.saveFeedback(request);

        // Then
        assertThat(response.feedbackContent()).isEqualTo("좋았습니다");
        verify(feedbackRepository).save(any(UserSatisfactionFeedback.class));
    }

    @Test
    @DisplayName("feedbackContent 없이 피드백 저장 → null로 정상 저장")
    void shouldSaveFeedback_WhenFeedbackContentIsNull() {
        // Given
        var request = new SatisfactionFeedbackRequest(
                1L, FeedbackType.CONSULTATION, SatisfactionStatus.DISSATISFIED, null);

        var savedFeedback = UserSatisfactionFeedback.builder()
                .sajuResult(SAJU_RESULT)
                .feedbackType(FeedbackType.CONSULTATION)
                .satisfactionStatus(SatisfactionStatus.DISSATISFIED)
                .feedbackContent(null)
                .build();

        given(sajuResultRepository.findById(1L)).willReturn(Optional.of(SAJU_RESULT));
        given(feedbackRepository.save(any(UserSatisfactionFeedback.class))).willReturn(savedFeedback);

        // When
        SatisfactionFeedbackResponse response = service.saveFeedback(request);

        // Then
        assertThat(response.feedbackContent()).isNull();
        verify(feedbackRepository).save(any(UserSatisfactionFeedback.class));
    }

    @Test
    @DisplayName("존재하지 않는 sajuResultId → SajuResultNotFoundException 발생")
    void shouldThrowSajuResultNotFoundException_WhenSajuResultNotFound() {
        // Given
        var request = new SatisfactionFeedbackRequest(
                999L, FeedbackType.CAREER_TIMING, SatisfactionStatus.SATISFIED, null);

        given(sajuResultRepository.findById(999L)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.saveFeedback(request))
                .isInstanceOf(SajuResultNotFoundException.class);
    }
}
