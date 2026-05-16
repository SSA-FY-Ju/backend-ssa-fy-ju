package ssafy.SSAju.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.entity.LoginAttempt;
import ssafy.SSAju.repository.LoginAttemptRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptEventListener {

    private final LoginAttemptRepository loginAttemptRepository;

    @EventListener
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLoginAttempt(LoginAttemptEvent event) {
        try {
            LoginAttempt attempt = LoginAttempt.builder()
                    .email(event.email())
                    .success(event.success())
                    .failureReason(event.failureReason())
                    .ipAddress(event.ipAddress())
                    .attemptedAt(event.attemptedAt())
                    .build();
            loginAttemptRepository.save(attempt);
        } catch (Exception e) {
            log.error("로그인 시도 기록 저장 실패: {}", e.getMessage());
        }
    }
}
