package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.dto.request.SatisfactionFeedbackRequest;
import ssafy.SSAju.dto.response.SatisfactionFeedbackResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.repository.UserSatisfactionFeedbackRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final SajuResultRepository sajuResultRepository;
    private final UserSatisfactionFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    public SatisfactionFeedbackResponse saveFeedback(SatisfactionFeedbackRequest request, Long userId) {
        log.info("피드백 저장 요청: sajuResultId={}, type={}", request.sajuResultId(), request.feedbackType());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        SajuResult sajuResult = sajuResultRepository.findByIdAndUser_Id(request.sajuResultId(), userId)
                .orElseThrow(() -> new SajuResultNotFoundException(
                        ErrorMessageConstants.SAJU_RESULT_NOT_FOUND.getMessage() + " id=" + request.sajuResultId()));

        UserSatisfactionFeedback feedback = UserSatisfactionFeedback.builder()
                .user(user)
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
