package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.dto.request.SatisfactionFeedbackRequest;
import ssafy.SSAju.dto.response.SatisfactionFeedbackResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.FeedbackNotAllowedException;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.repository.UserSatisfactionFeedbackRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final CompanyCompatibilityRepository compatibilityRepository;
    private final CareerConsultationRepository consultationRepository;
    private final UserSatisfactionFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    @Transactional
    public SatisfactionFeedbackResponse saveFeedback(SatisfactionFeedbackRequest request, Long userId) {
        log.info("피드백 저장 요청: analysisId={}, type={}", request.analysisId(), request.feedbackType());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        UserSatisfactionFeedback feedback = switch (request.feedbackType()) {
            case COMPATIBILITY -> {
                CompanyCompatibility compatibility = compatibilityRepository
                        .findByIdAndUser(request.analysisId(), user)
                        .orElseThrow(() -> new SajuResultNotFoundException(
                                ErrorMessageConstants.SAJU_RESULT_NOT_FOUND.getMessage()
                                        + " id=" + request.analysisId()));
                yield UserSatisfactionFeedback.builder()
                        .user(user)
                        .companyCompatibility(compatibility)
                        .feedbackType(request.feedbackType())
                        .satisfactionStatus(request.satisfactionStatus())
                        .feedbackContent(request.feedbackContent())
                        .build();
            }
            case CONSULTATION -> {
                // C-4/B1: DB 레벨 소유권 검증 — lazy loading chain(getSajuResult().getUser()) 제거.
                // findByIdAndAccessibleByUser가 UserSajuAccess EXISTS 서브쿼리로 소유권을 확인하므로
                // 트랜잭션 여부와 무관하게 안전하게 IDOR를 차단합니다.
                CareerConsultation consultation = consultationRepository
                        .findByIdAndAccessibleByUser(request.analysisId(), userId)
                        .orElseThrow(() -> new SajuResultNotFoundException(
                                ErrorMessageConstants.SAJU_RESULT_NOT_FOUND.getMessage()
                                        + " id=" + request.analysisId()));
                yield UserSatisfactionFeedback.builder()
                        .user(user)
                        .careerConsultation(consultation)
                        .feedbackType(request.feedbackType())
                        .satisfactionStatus(request.satisfactionStatus())
                        .feedbackContent(request.feedbackContent())
                        .build();
            }
            case CAREER_TIMING ->
                throw new FeedbackNotAllowedException("관운 분석은 피드백 대상이 아닙니다.");
        };

        UserSatisfactionFeedback saved = feedbackRepository.save(feedback);
        log.info("피드백 저장 완료: feedbackId={}", saved.getId());

        return new SatisfactionFeedbackResponse(saved.getId(), saved.getCreatedAt(), saved.getFeedbackContent());
    }
}
