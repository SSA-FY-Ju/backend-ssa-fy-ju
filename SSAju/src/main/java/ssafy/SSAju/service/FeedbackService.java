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

        // TODO: Phase 2 인증 추가 시 다음과 같이 수정 필요
        // 1. User 파라미터 추가 (FeedbackController에서 @AuthenticationPrincipal로 전달)
        // 2. sajuResultRepository.findById() → sajuResultRepository.findByIdAndUser(sajuResultId, user)
        //    (사용자가 자신의 SajuResult에만 피드백 가능하도록 범위 제한)
        // 3. UserSatisfactionFeedback에 user 필드 설정

        SajuResult sajuResult = sajuResultRepository.findById(request.sajuResultId())
                .orElseThrow(() -> new SajuResultNotFoundException(
                        ErrorMessageConstants.SAJU_RESULT_NOT_FOUND.getMessage() + " id=" + request.sajuResultId()));

        UserSatisfactionFeedback feedback = UserSatisfactionFeedback.builder()
                .sajuResult(sajuResult)
                // TODO: Phase 2에서 .user(user) 추가
                .feedbackType(request.feedbackType())
                .satisfactionStatus(request.satisfactionStatus())
                .feedbackContent(request.feedbackContent())
                .build();

        UserSatisfactionFeedback saved = feedbackRepository.save(feedback);
        log.info("피드백 저장 완료: feedbackId={}", saved.getId());

        return new SatisfactionFeedbackResponse(saved.getId(), saved.getCreatedAt(), saved.getFeedbackContent());
    }
}
