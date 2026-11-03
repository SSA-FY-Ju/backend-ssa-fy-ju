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
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.FeedbackNotFoundException;
import ssafy.SSAju.exception.InvalidFeedbackTypeException;

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

    public Page<FeedbackListDTO> getFeedbackList(AnalysisType feedbackType, int page, int size) {
        if (feedbackType == AnalysisType.SAJU) {
            throw new InvalidFeedbackTypeException(ErrorMessageConstants.INVALID_FEEDBACK_TYPE.getMessage());
        }
        Pageable pageable = paginationUtil.of(page, size);
        Page<FeedbackListDTO> result = feedbackRepository.findFeedbackByType(feedbackType, pageable);
        log.debug("피드백 목록 조회: type={}, page={}, count={}", feedbackType, page, result.getNumberOfElements());
        return result;
    }

    public FeedbackDetailDTO getFeedbackWithAnalysis(Long feedbackId) {
        UserSatisfactionFeedback feedback = feedbackRepository
                .findFeedbackWithAnalysis(feedbackId)
                .orElseThrow(() -> new FeedbackNotFoundException(
                        ErrorMessageConstants.FEEDBACK_NOT_FOUND.getMessage() + " (id=" + feedbackId + ")"));

        return toDetailDTO(feedback);
    }

    /**
     * 응답의 feedbackType은 기존 화면 호환을 위해 유지하되 값은 연결된 분석의 유형에서 얻는다.
     */
    private FeedbackDetailDTO toDetailDTO(UserSatisfactionFeedback f) {
        Saju saju = f.getSaju();
        String analysisType = saju.getAnalysisType().name();

        return new FeedbackDetailDTO(
                f.getId(),
                f.getUser() != null ? f.getUser().getId() : null,
                analysisType,
                f.getSatisfactionStatus().name(),
                f.getFeedbackContent(),
                f.getCreatedAt(),
                analysisType,
                saju.getId(),
                saju.getCreatedAt()
        );
    }
}
