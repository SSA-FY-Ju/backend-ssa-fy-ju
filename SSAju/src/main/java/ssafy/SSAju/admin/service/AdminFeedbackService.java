package ssafy.SSAju.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.admin.dto.FeedbackDetailDTO;
import ssafy.SSAju.admin.dto.FeedbackListDTO;
import ssafy.SSAju.admin.dto.FeedbackStatDTO;
import ssafy.SSAju.admin.repository.AdminFeedbackQueryRepository;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.career.enums.FeedbackType;
import ssafy.SSAju.exception.FeedbackNotFoundException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminFeedbackService extends AdminBaseService {

    private final AdminFeedbackQueryRepository feedbackRepository;
    private final AdminPaginationUtil paginationUtil;

    public FeedbackStatDTO getFeedbackStats() {
        FeedbackStatDTO stats = feedbackRepository.findFeedbackStats();
        log.debug("피드백 통계 조회: total={}", stats.totalFeedbackCount());
        return stats;
    }

    public Page<FeedbackListDTO> getFeedbackList(FeedbackType feedbackType, int page, int size) {
        if (feedbackType == FeedbackType.CAREER_TIMING) {
            throw new IllegalArgumentException("CAREER_TIMING은 피드백 조회 대상이 아닙니다.");
        }
        Pageable pageable = paginationUtil.of(page, size);
        Page<FeedbackListDTO> result = feedbackRepository.findFeedbackByType(feedbackType, pageable);
        log.debug("피드백 목록 조회: type={}, page={}, count={}", feedbackType, page, result.getNumberOfElements());
        return result;
    }

    public FeedbackDetailDTO getFeedbackWithAnalysis(Long feedbackId) {
        UserSatisfactionFeedback feedback = feedbackRepository
                .findFeedbackWithAnalysis(feedbackId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new FeedbackNotFoundException(
                        "피드백을 찾을 수 없습니다: id=" + feedbackId));

        return toDetailDTO(feedback);
    }

    private FeedbackDetailDTO toDetailDTO(UserSatisfactionFeedback f) {
        CareerConsultation consultation = f.getCareerConsultation();
        CompanyCompatibility compatibility = f.getCompanyCompatibility();

        String analysisType = null;
        Long analysisId = null;
        java.time.Instant analysisCreatedAt = null;

        if (consultation != null) {
            analysisType = "CONSULTATION";
            analysisId = consultation.getId();
            analysisCreatedAt = consultation.getGeneratedAt();
        } else if (compatibility != null) {
            analysisType = "COMPATIBILITY";
            analysisId = compatibility.getId();
            analysisCreatedAt = compatibility.getCreatedAt();
        }

        return new FeedbackDetailDTO(
                f.getId(),
                f.getUser() != null ? f.getUser().getId() : null,
                f.getFeedbackType().name(),
                f.getSatisfactionStatus().name(),
                f.getFeedbackContent(),
                f.getCreatedAt(),
                analysisType,
                analysisId,
                analysisCreatedAt
        );
    }
}
