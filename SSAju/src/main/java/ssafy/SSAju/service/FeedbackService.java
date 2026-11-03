package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.dto.request.SatisfactionFeedbackRequest;
import ssafy.SSAju.career.dto.response.SatisfactionFeedbackResponse;
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.FeedbackAlreadyExistsException;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.repository.UserSatisfactionFeedbackRepository;
import ssafy.SSAju.util.DatabaseConstraints;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final SajuRepository sajuRepository;
    private final UserSatisfactionFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    /**
     * 분석 한 건에 대한 만족도 피드백을 저장한다.
     *
     * <p>허용 여부는 요청값이 아니라 연결된 분석의 유형으로 판정한다. 없는 분석·타인의 분석·관운
     * 분석을 모두 같은 미존재 오류로 처리하는 이유는, 셋을 구분해 알려주면 남의 분석 ID가 실제로
     * 존재하는지 드러나기 때문이다.
     *
     * <p>중복 제출은 {@code saju_id} UNIQUE 제약으로 막는다. 사전 조회만으로는 동시 제출을 막을 수
     * 없어, 알려진 제약 위반만 409로 바꾸고 다른 무결성 오류는 그대로 전파한다.
     */
    @Transactional
    public SatisfactionFeedbackResponse saveFeedback(SatisfactionFeedbackRequest request, Long userId) {
        log.info("피드백 저장 요청: userId={}, analysisId={}", userId, request.analysisId());

        User user = requireActiveUser(userId);
        Saju saju = sajuRepository.findByIdAndUserId(request.analysisId(), userId)
                .orElseThrow(() -> new SajuResultNotFoundException(
                        ErrorMessageConstants.SAJU_RESULT_NOT_FOUND.getMessage()));
        if (saju.getAnalysisType() == AnalysisType.SAJU) {
            throw new SajuResultNotFoundException(ErrorMessageConstants.SAJU_RESULT_NOT_FOUND.getMessage());
        }

        // 같은 분석에 이미 피드백이 있으면 제약 위반 전에 먼저 거부한다(동시 제출은 아래 제약이 막는다).
        if (feedbackRepository.existsBySajuId(saju.getId())) {
            throw new FeedbackAlreadyExistsException(
                    ErrorMessageConstants.FEEDBACK_ALREADY_EXISTS.getMessage());
        }

        UserSatisfactionFeedback saved;
        try {
            saved = feedbackRepository.saveAndFlush(UserSatisfactionFeedback.builder()
                    .user(user)
                    .saju(saju)
                    .satisfactionStatus(request.satisfactionStatus())
                    .feedbackContent(request.feedbackContent())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 알려진 중복(saju_id UNIQUE)만 409로 바꾸고 FK 위반 등 다른 무결성 오류는 그대로 전파한다.
            Throwable rootCause = NestedExceptionUtils.getRootCause(e);
            if (rootCause instanceof ConstraintViolationException cve
                    && cve.getConstraintName() != null
                    && cve.getConstraintName().contains(DatabaseConstraints.FEEDBACK_SAJU_UNIQUE)) {
                throw new FeedbackAlreadyExistsException(
                        ErrorMessageConstants.FEEDBACK_ALREADY_EXISTS.getMessage(), e);
            }
            throw e;
        }

        log.info("피드백 저장 완료: feedbackId={}", saved.getId());
        return new SatisfactionFeedbackResponse(saved.getId(), saved.getCreatedAt(), saved.getFeedbackContent());
    }

    /**
     * 사용자 행을 잠근 채 활성 상태를 확인한다 — 탈퇴가 먼저 커밋됐다면 새 피드백을 남기지 않는다.
     */
    private User requireActiveUser(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(ErrorMessageConstants.USER_NOT_FOUND.getMessage()));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UserNotFoundException(ErrorMessageConstants.USER_NOT_FOUND.getMessage());
        }
        return user;
    }
}
