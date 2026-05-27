package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.career.enums.FeedbackType;
import ssafy.SSAju.career.enums.SatisfactionStatus;
import ssafy.SSAju.dto.request.SatisfactionFeedbackRequest;
import ssafy.SSAju.dto.response.SatisfactionFeedbackResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.FeedbackNotAllowedException;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.repository.UserSatisfactionFeedbackRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackService 단위 테스트")
class FeedbackServiceTest {

    @Mock private CompanyCompatibilityRepository compatibilityRepository;
    @Mock private CareerConsultationRepository consultationRepository;
    @Mock private UserSatisfactionFeedbackRepository feedbackRepository;
    @Mock private UserRepository userRepository;

    private FeedbackService service;

    private static final Long USER_ID = 1L;
    private static final User MOCK_USER = User.builder()
            .email("test@test.com")
            .passwordHash("hash")
            .name("테스트")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .termsAgreedAt(Instant.now())
            .privacyAgreedAt(Instant.now())
            .build();

    @BeforeEach
    void setUp() {
        service = new FeedbackService(compatibilityRepository, consultationRepository,
                feedbackRepository, userRepository);
    }

    @Test
    @DisplayName("COMPATIBILITY 피드백 → 저장 후 응답 반환")
    void shouldSaveFeedback_WhenCompatibilityType() {
        var request = new SatisfactionFeedbackRequest(
                1L, FeedbackType.COMPATIBILITY, SatisfactionStatus.SATISFIED, "좋았습니다");

        CompanyCompatibility compatibility = CompanyCompatibility.builder()
                .userProfile(UserProfile.builder()
                        .birthDate(LocalDate.of(1990, 10, 10))
                        .birthTime(LocalTime.of(14, 30))
                        .build())
                .user(MOCK_USER)
                .companyName("테스트 기업")
                .build();

        var savedFeedback = UserSatisfactionFeedback.builder()
                .user(MOCK_USER)
                .companyCompatibility(compatibility)
                .feedbackType(FeedbackType.COMPATIBILITY)
                .satisfactionStatus(SatisfactionStatus.SATISFIED)
                .feedbackContent("좋았습니다")
                .build();

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(MOCK_USER));
        given(compatibilityRepository.findByIdAndUser(1L, MOCK_USER)).willReturn(Optional.of(compatibility));
        given(feedbackRepository.save(any(UserSatisfactionFeedback.class))).willReturn(savedFeedback);

        SatisfactionFeedbackResponse response = service.saveFeedback(request, USER_ID);

        assertThat(response.feedbackContent()).isEqualTo("좋았습니다");
        verify(feedbackRepository).save(any(UserSatisfactionFeedback.class));
    }

    @Test
    @DisplayName("CONSULTATION 피드백 → 저장 후 응답 반환")
    void shouldSaveFeedback_WhenConsultationType() {
        var request = new SatisfactionFeedbackRequest(
                1L, FeedbackType.CONSULTATION, SatisfactionStatus.DISSATISFIED, null);

        CareerConsultation consultation = mock(CareerConsultation.class);

        var savedFeedback = UserSatisfactionFeedback.builder()
                .user(MOCK_USER)
                .careerConsultation(consultation)
                .feedbackType(FeedbackType.CONSULTATION)
                .satisfactionStatus(SatisfactionStatus.DISSATISFIED)
                .feedbackContent(null)
                .build();

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(MOCK_USER));
        // C-4: DB 레벨 소유권 검증 메서드 사용
        given(consultationRepository.findByIdAndSajuResult_User_Id(1L, USER_ID))
                .willReturn(Optional.of(consultation));
        given(feedbackRepository.save(any(UserSatisfactionFeedback.class))).willReturn(savedFeedback);

        SatisfactionFeedbackResponse response = service.saveFeedback(request, USER_ID);

        assertThat(response.feedbackContent()).isNull();
        verify(feedbackRepository).save(any(UserSatisfactionFeedback.class));
    }

    @Test
    @DisplayName("CONSULTATION — 다른 유저(id=999L)의 데이터 접근 시도 → SajuResultNotFoundException")
    void shouldThrow_WhenConsultationBelongsToOtherUser() {
        // Given — 요청 userId=1L이지만 consultationId=1L은 userId=1L 소유가 아님
        var request = new SatisfactionFeedbackRequest(
                1L, FeedbackType.CONSULTATION, SatisfactionStatus.SATISFIED, null);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(MOCK_USER));
        // DB 레벨 소유권 검증: userId=1L로 조회 시 empty 반환 (다른 유저 소유)
        given(consultationRepository.findByIdAndSajuResult_User_Id(1L, USER_ID))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.saveFeedback(request, USER_ID))
                .isInstanceOf(SajuResultNotFoundException.class);
    }

    @Test
    @DisplayName("CAREER_TIMING 피드백 요청 → FeedbackNotAllowedException 발생")
    void shouldThrowFeedbackNotAllowedException_WhenCareerTimingType() {
        var request = new SatisfactionFeedbackRequest(
                1L, FeedbackType.CAREER_TIMING, SatisfactionStatus.SATISFIED, null);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(MOCK_USER));

        assertThatThrownBy(() -> service.saveFeedback(request, USER_ID))
                .isInstanceOf(FeedbackNotAllowedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 ID → UserNotFoundException 발생")
    void shouldThrowUserNotFoundException_WhenUserNotFound() {
        var request = new SatisfactionFeedbackRequest(
                1L, FeedbackType.COMPATIBILITY, SatisfactionStatus.SATISFIED, null);

        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveFeedback(request, 999L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("존재하지 않는 compatibilityId → SajuResultNotFoundException 발생")
    void shouldThrowSajuResultNotFoundException_WhenCompatibilityNotFound() {
        var request = new SatisfactionFeedbackRequest(
                999L, FeedbackType.COMPATIBILITY, SatisfactionStatus.SATISFIED, null);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(MOCK_USER));
        given(compatibilityRepository.findByIdAndUser(999L, MOCK_USER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveFeedback(request, USER_ID))
                .isInstanceOf(SajuResultNotFoundException.class);
    }
}
