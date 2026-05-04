package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.dto.request.SatisfactionFeedbackRequest;
import ssafy.SSAju.dto.response.SatisfactionFeedbackResponse;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserSatisfactionFeedbackRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final SajuResultRepository sajuResultRepository;
    private final UserSatisfactionFeedbackRepository feedbackRepository;

    public SatisfactionFeedbackResponse saveFeedback(SatisfactionFeedbackRequest request) {
        log.info("피드백 저장 요청: sajuResultId={}, type={}", request.sajuResultId(), request.feedbackType());

        SajuResult sajuResult = sajuResultRepository.findById(request.sajuResultId())
                .orElseThrow(() -> new SajuResultNotFoundException(
                        ErrorMessageConstants.SAJU_RESULT_NOT_FOUND.getMessage() + " id=" + request.sajuResultId()));

        UserSatisfactionFeedback feedback = UserSatisfactionFeedback.builder()
                .sajuResult(sajuResult)
                .feedbackType(request.feedbackType())
                .satisfactionStatus(request.satisfactionStatus())
                .feedbackContent(request.feedbackContent())
                .build();

        UserSatisfactionFeedback saved = feedbackRepository.save(feedback);
        log.info("피드백 저장 완료: feedbackId={}", saved.getId());

        return new SatisfactionFeedbackResponse(saved.getId(), saved.getCreatedAt(), saved.getFeedbackContent());
    }
}
