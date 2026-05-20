package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.repository.UserSatisfactionFeedbackRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisResultMaskingService {

    private final UserSatisfactionFeedbackRepository userSatisfactionFeedbackRepository;

    @Transactional
    public void maskForUser(User user) {
        userSatisfactionFeedbackRepository.deleteAllByUser(user);
        log.info("만족도 피드백 삭제 완료: userId={}", user.getId());
    }
}
