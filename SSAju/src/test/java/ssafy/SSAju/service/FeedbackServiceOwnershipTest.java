package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.career.dto.request.SatisfactionFeedbackRequest;
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.SatisfactionStatus;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.repository.UserSatisfactionFeedbackRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 피드백 대상의 소유권은 공유 접근 관계가 아니라 {@code saju.user_id}로 직접 판단한다.
 *
 * <p>없는 분석·타인의 분석·관운 분석을 모두 같은 미존재 오류로 돌려주는지 확인한다 — 셋을 구분해
 * 알려주면 남의 분석 ID가 실제로 존재하는지 드러난다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackService 소유권 검증 테스트")
class FeedbackServiceOwnershipTest {

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
        user = UnifiedAnalysisTestSupport.newUser("owner@test.com");
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
    }

    @Test
    @DisplayName("타인의 분석은 소유자 조건에서 걸러져 거부된다")
    void shouldDenyOtherOwnersAnalysis() {
        // Given: 소유자 조건이 함께 걸리므로 다른 사용자의 분석은 조회되지 않는다
        given(sajuRepository.findByIdAndUserId(ANALYSIS_ID, USER_ID)).willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.saveFeedback(request(), USER_ID))
                .isInstanceOf(SajuResultNotFoundException.class);
        verify(feedbackRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("관운 분석은 소유자여도 피드백 대상이 아니다")
    void shouldDenySajuTypeEvenForOwner() {
        // Given
        Saju saju = mock(Saju.class);
        given(saju.getAnalysisType()).willReturn(AnalysisType.SAJU);
        given(sajuRepository.findByIdAndUserId(ANALYSIS_ID, USER_ID)).willReturn(Optional.of(saju));

        // When / Then
        assertThatThrownBy(() -> service.saveFeedback(request(), USER_ID))
                .isInstanceOf(SajuResultNotFoundException.class);
        verify(feedbackRepository, never()).saveAndFlush(any());
    }

    private SatisfactionFeedbackRequest request() {
        return new SatisfactionFeedbackRequest(ANALYSIS_ID, SatisfactionStatus.SATISFIED, null);
    }
}
