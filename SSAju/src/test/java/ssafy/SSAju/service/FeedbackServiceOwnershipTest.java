package ssafy.SSAju.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.career.dto.request.SatisfactionFeedbackRequest;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.SatisfactionStatus;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.repository.UserSatisfactionFeedbackRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * B1: SajuResult가 여러 사용자가 공유하는 정본으로 바뀌면서, 피드백 대상(CareerConsultation)에 대한
 * 소유권은 더 이상 SajuResult.user가 아니라 UserSajuAccess 매핑 존재 여부로 판단한다(US6, T039).
 * UserSajuAccess 매핑이 없는 사용자의 피드백 접근은 CareerConsultationRepository의 EXISTS 서브쿼리
 * (findByIdAndAccessibleByUser)에서 애초에 empty로 걸러지므로, 그 이후의 흐름(FeedbackService)이
 * 이를 SajuResultNotFoundException으로 안전하게 거부하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackService — UserSajuAccess 기반 소유권 검증 테스트")
class FeedbackServiceOwnershipTest {

    @Mock private CompanyCompatibilityRepository compatibilityRepository;
    @Mock private CareerConsultationRepository consultationRepository;
    @Mock private UserSatisfactionFeedbackRepository feedbackRepository;
    @Mock private UserRepository userRepository;

    private FeedbackService service;

    private static final Long USER_ID = 1L;
    private static final Long ANALYSIS_ID = 10L;
    private static final User MOCK_USER = User.builder()
            .email("test@test.com")
            .passwordHash("hash")
            .name("테스트")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .termsAgreedAt(Instant.now())
            .privacyAgreedAt(Instant.now())
            .build();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new FeedbackService(compatibilityRepository, consultationRepository,
                feedbackRepository, userRepository);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(MOCK_USER));
    }

    @Test
    @DisplayName("UserSajuAccess 매핑이 없는 사용자의 CONSULTATION 피드백 접근은 거부된다")
    void consultationFeedback_deniedWithoutAccessMapping() {
        // Given — 다른 사용자의 정본이므로 findByIdAndAccessibleByUser가 EXISTS 서브쿼리에서 empty를 반환
        var request = new SatisfactionFeedbackRequest(
                ANALYSIS_ID, AnalysisType.CAREER_CONSULTATION, SatisfactionStatus.SATISFIED, null);
        given(consultationRepository.findByIdAndAccessibleByUser(ANALYSIS_ID, USER_ID))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.saveFeedback(request, USER_ID))
                .isInstanceOf(SajuResultNotFoundException.class);
    }

    @Test
    @DisplayName("UserSajuAccess 매핑이 없는 사용자의 COMPATIBILITY 피드백 접근은 거부된다")
    void compatibilityFeedback_deniedWithoutOwnership() {
        // Given — CompanyCompatibility는 자체 user FK를 가지므로 findByIdAndUser가 소유권을 직접 확인
        var request = new SatisfactionFeedbackRequest(
                ANALYSIS_ID, AnalysisType.COMPANY_COMPATIBILITY, SatisfactionStatus.SATISFIED, null);
        given(compatibilityRepository.findByIdAndUser(ANALYSIS_ID, MOCK_USER)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.saveFeedback(request, USER_ID))
                .isInstanceOf(SajuResultNotFoundException.class);
    }
}
