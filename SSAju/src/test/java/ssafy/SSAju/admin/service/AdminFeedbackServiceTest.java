package ssafy.SSAju.admin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ssafy.SSAju.admin.dto.FeedbackDetailDTO;
import ssafy.SSAju.admin.dto.FeedbackListDTO;
import ssafy.SSAju.admin.dto.FeedbackStatDTO;
import ssafy.SSAju.admin.repository.AdminFeedbackQueryRepository;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.career.enums.FeedbackType;
import ssafy.SSAju.career.enums.SatisfactionStatus;
import ssafy.SSAju.exception.FeedbackNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminFeedbackService 단위 테스트")
class AdminFeedbackServiceTest {

    @Mock
    private AdminFeedbackQueryRepository feedbackRepository;

    @Mock
    private AdminPaginationUtil paginationUtil;

    @InjectMocks
    private AdminFeedbackService adminFeedbackService;

    @Test
    @DisplayName("getFeedbackStats - Repository 결과를 그대로 반환")
    void getFeedbackStats_returnsRepositoryResult() {
        FeedbackStatDTO stub = new FeedbackStatDTO(
                Map.of("CONSULTATION", 10L),
                Map.of("CONSULTATION", 3L),
                13L
        );
        given(feedbackRepository.findFeedbackStats()).willReturn(stub);

        FeedbackStatDTO result = adminFeedbackService.getFeedbackStats();

        assertThat(result.totalFeedbackCount()).isEqualTo(13L);
        assertThat(result.satisfiedCountByFeedbackType()).containsEntry("CONSULTATION", 10L);
        verify(feedbackRepository).findFeedbackStats();
    }

    @Test
    @DisplayName("getFeedbackList - type=null → Repository에 null 전달")
    void getFeedbackList_nullType_passesNullToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        given(paginationUtil.of(anyInt(), anyInt())).willReturn(pageable);
        given(feedbackRepository.findFeedbackByType(isNull(), eq(pageable)))
                .willReturn(new PageImpl<>(List.of()));

        Page<FeedbackListDTO> result = adminFeedbackService.getFeedbackList(null, 0, 20);

        assertThat(result.getContent()).isEmpty();
        verify(feedbackRepository).findFeedbackByType(isNull(), eq(pageable));
    }

    @Test
    @DisplayName("getFeedbackList - type=CONSULTATION → FeedbackType.CONSULTATION으로 변환")
    void getFeedbackList_withType_convertsEnum() {
        Pageable pageable = PageRequest.of(0, 20);
        FeedbackListDTO item = new FeedbackListDTO(
                1L, 10L, "좋아요", SatisfactionStatus.SATISFIED, FeedbackType.CONSULTATION, Instant.now());
        given(paginationUtil.of(anyInt(), anyInt())).willReturn(pageable);
        given(feedbackRepository.findFeedbackByType(eq(FeedbackType.CONSULTATION), eq(pageable)))
                .willReturn(new PageImpl<>(List.of(item)));

        Page<FeedbackListDTO> result = adminFeedbackService.getFeedbackList(FeedbackType.CONSULTATION, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).feedbackType()).isEqualTo(FeedbackType.CONSULTATION);
    }

    @Test
    @DisplayName("getFeedbackList - type=CAREER_TIMING → IllegalArgumentException (피드백 불가 정책)")
    void getFeedbackList_careerTimingType_throwsException() {
        assertThatThrownBy(() -> adminFeedbackService.getFeedbackList(FeedbackType.CAREER_TIMING, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CAREER_TIMING");

        verifyNoInteractions(feedbackRepository);
    }

    @Test
    @DisplayName("getFeedbackWithAnalysis - CareerConsultation 연결된 피드백 → analysisType=CONSULTATION")
    void getFeedbackWithAnalysis_consultationLinked_returnsConsultationType() {
        UserSatisfactionFeedback feedback = mock(UserSatisfactionFeedback.class);
        CareerConsultation consultation = mock(CareerConsultation.class);
        given(feedback.getId()).willReturn(1L);
        given(feedback.getUser()).willReturn(null);
        given(feedback.getFeedbackType()).willReturn(FeedbackType.CONSULTATION);
        given(feedback.getSatisfactionStatus()).willReturn(SatisfactionStatus.SATISFIED);
        given(feedback.getFeedbackContent()).willReturn("좋았습니다");
        given(feedback.getCreatedAt()).willReturn(Instant.now());
        given(feedback.getCareerConsultation()).willReturn(consultation);
        given(feedback.getCompanyCompatibility()).willReturn(null);
        given(consultation.getId()).willReturn(100L);
        given(consultation.getGeneratedAt()).willReturn(Instant.now());
        given(feedbackRepository.findFeedbackWithAnalysis(1L)).willReturn(List.of(feedback));

        FeedbackDetailDTO result = adminFeedbackService.getFeedbackWithAnalysis(1L);

        assertThat(result.feedbackId()).isEqualTo(1L);
        assertThat(result.analysisType()).isEqualTo("CONSULTATION");
        assertThat(result.analysisId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getFeedbackWithAnalysis - CompanyCompatibility 연결된 피드백 → analysisType=COMPATIBILITY")
    void getFeedbackWithAnalysis_compatibilityLinked_returnsCompatibilityType() {
        UserSatisfactionFeedback feedback = mock(UserSatisfactionFeedback.class);
        CompanyCompatibility compatibility = mock(CompanyCompatibility.class);
        given(feedback.getId()).willReturn(2L);
        given(feedback.getUser()).willReturn(null);
        given(feedback.getFeedbackType()).willReturn(FeedbackType.COMPATIBILITY);
        given(feedback.getSatisfactionStatus()).willReturn(SatisfactionStatus.DISSATISFIED);
        given(feedback.getFeedbackContent()).willReturn("별로였어요");
        given(feedback.getCreatedAt()).willReturn(Instant.now());
        given(feedback.getCareerConsultation()).willReturn(null);
        given(feedback.getCompanyCompatibility()).willReturn(compatibility);
        given(compatibility.getId()).willReturn(200L);
        given(compatibility.getCreatedAt()).willReturn(Instant.now());
        given(feedbackRepository.findFeedbackWithAnalysis(2L)).willReturn(List.of(feedback));

        FeedbackDetailDTO result = adminFeedbackService.getFeedbackWithAnalysis(2L);

        assertThat(result.analysisType()).isEqualTo("COMPATIBILITY");
        assertThat(result.analysisId()).isEqualTo(200L);
        assertThat(result.satisfactionStatus()).isEqualTo("DISSATISFIED");
    }

    @Test
    @DisplayName("getFeedbackWithAnalysis - 존재하지 않는 id → FeedbackNotFoundException")
    void getFeedbackWithAnalysis_notFound_throwsException() {
        given(feedbackRepository.findFeedbackWithAnalysis(999L)).willReturn(List.of());

        assertThatThrownBy(() -> adminFeedbackService.getFeedbackWithAnalysis(999L))
                .isInstanceOf(FeedbackNotFoundException.class)
                .hasMessageContainingAll("피드백", "999");
    }

    @Test
    @DisplayName("getFeedbackList - 페이지네이션 파라미터 AdminPaginationUtil에 위임")
    void getFeedbackList_delegatesPaginationToUtil() {
        Pageable pageable = PageRequest.of(2, 10);
        given(paginationUtil.of(2, 10)).willReturn(pageable);
        given(feedbackRepository.findFeedbackByType(any(), eq(pageable)))
                .willReturn(new PageImpl<>(List.of()));

        adminFeedbackService.getFeedbackList(null, 2, 10);

        verify(paginationUtil).of(2, 10);
    }
}
