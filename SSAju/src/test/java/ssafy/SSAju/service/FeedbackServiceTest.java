package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import ssafy.SSAju.career.dto.request.SatisfactionFeedbackRequest;
import ssafy.SSAju.career.dto.response.SatisfactionFeedbackResponse;
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.SatisfactionStatus;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.FeedbackAlreadyExistsException;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.repository.UserSatisfactionFeedbackRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackService 단위 테스트")
class FeedbackServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ANALYSIS_ID = 10L;

    @Mock private SajuRepository sajuRepository;
    @Mock private UserSatisfactionFeedbackRepository feedbackRepository;
    @Mock private UserRepository userRepository;

    private FeedbackService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new FeedbackService(sajuRepository, feedbackRepository, userRepository);
        user = UnifiedAnalysisTestSupport.newUser("feedback@test.com");
    }

    @Test
    @DisplayName("컨설팅·궁합 분석에는 피드백을 남길 수 있다")
    void shouldSaveFeedbackForAllowedTypes() {
        for (AnalysisType type : new AnalysisType[]{
                AnalysisType.CAREER_CONSULTATION, AnalysisType.COMPANY_COMPATIBILITY}) {
            // Given
            givenActiveUser();
            givenAnalysis(type);
            given(feedbackRepository.existsBySajuId(ANALYSIS_ID)).willReturn(false);
            UserSatisfactionFeedback saved = savedFeedback();
            given(feedbackRepository.saveAndFlush(any())).willReturn(saved);

            // When
            SatisfactionFeedbackResponse response = service.saveFeedback(request(), USER_ID);

            // Then
            assertThat(response.feedbackId()).isEqualTo(99L);
        }
    }

    @Test
    @DisplayName("관운 분석 대상 피드백은 미존재와 같은 오류로 거부한다")
    void shouldRejectFeedbackForSajuType() {
        // Given
        givenActiveUser();
        givenAnalysis(AnalysisType.SAJU);

        // When / Then
        assertThatThrownBy(() -> service.saveFeedback(request(), USER_ID))
                .isInstanceOf(SajuResultNotFoundException.class);
        verify(feedbackRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("없는 분석이나 타인의 분석은 거부한다")
    void shouldRejectMissingOrOtherOwnersAnalysis() {
        // Given
        givenActiveUser();
        given(sajuRepository.findByIdAndUserId(ANALYSIS_ID, USER_ID)).willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.saveFeedback(request(), USER_ID))
                .isInstanceOf(SajuResultNotFoundException.class);
        verify(feedbackRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("같은 분석에 두 번째 피드백은 409로 거부한다")
    void shouldRejectDuplicateFeedback() {
        // Given
        givenActiveUser();
        givenAnalysis(AnalysisType.CAREER_CONSULTATION);
        given(feedbackRepository.existsBySajuId(ANALYSIS_ID)).willReturn(true);

        // When / Then
        assertThatThrownBy(() -> service.saveFeedback(request(), USER_ID))
                .isInstanceOf(FeedbackAlreadyExistsException.class);
        verify(feedbackRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("중복 외의 무결성 오류는 409로 바꾸지 않고 전파한다")
    void shouldPropagateOtherIntegrityViolations() {
        // Given: 제약 이름을 알 수 없는 무결성 오류
        givenActiveUser();
        givenAnalysis(AnalysisType.CAREER_CONSULTATION);
        given(feedbackRepository.existsBySajuId(ANALYSIS_ID)).willReturn(false);
        willThrow(new DataIntegrityViolationException("foreign key violation"))
                .given(feedbackRepository).saveAndFlush(any());

        // When / Then
        assertThatThrownBy(() -> service.saveFeedback(request(), USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(FeedbackAlreadyExistsException.class);
    }

    @Test
    @DisplayName("탈퇴했거나 비활성인 사용자의 피드백은 거부한다")
    void shouldRejectFeedbackForInactiveUser() {
        // Given
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.saveFeedback(request(), USER_ID))
                .isInstanceOf(UserNotFoundException.class);

        // Given: 조회는 되지만 비활성
        User inactive = User.builder()
                .email("inactive@test.com").passwordHash("hash").name("비활성")
                .role(UserRole.USER).status(UserStatus.INACTIVE)
                .termsAgreedAt(Instant.now()).privacyAgreedAt(Instant.now())
                .build();
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(inactive));

        // When / Then
        assertThatThrownBy(() -> service.saveFeedback(request(), USER_ID))
                .isInstanceOf(UserNotFoundException.class);
        verify(sajuRepository, never()).findByIdAndUserId(any(), any());
    }

    private void givenActiveUser() {
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
    }

    private void givenAnalysis(AnalysisType type) {
        Saju saju = mock(Saju.class);
        given(saju.getAnalysisType()).willReturn(type);
        if (type != AnalysisType.SAJU) {
            given(saju.getId()).willReturn(ANALYSIS_ID);
        }
        given(sajuRepository.findByIdAndUserId(ANALYSIS_ID, USER_ID)).willReturn(Optional.of(saju));
    }

    private UserSatisfactionFeedback savedFeedback() {
        UserSatisfactionFeedback feedback = mock(UserSatisfactionFeedback.class);
        given(feedback.getId()).willReturn(99L);
        return feedback;
    }

    private SatisfactionFeedbackRequest request() {
        return new SatisfactionFeedbackRequest(ANALYSIS_ID, SatisfactionStatus.SATISFIED, "좋았습니다");
    }
}
