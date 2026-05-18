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

/**
 * 로그인 시도 이벤트 리스너.
 *
 * {@link LoginAttemptEvent}를 수신하여 로그인 시도 기록을 데이터베이스에 저장합니다.
 * 비동기 처리({@code @Async})와 독립 트랜잭션({@code REQUIRES_NEW})을 사용하여
 * 메인 로그인 로직에 영향을 주지 않으면서도 모든 시도를 기록합니다.
 *
 * <p><b>트랜잭션 격리 설명:</b>
 * <ul>
 *   <li>메인 로그인 트랜잭션(AuthService.login())이 실패해도 LoginAttempt 기록은 저장됨</li>
 *   <li>LoginAttempt 저장 실패가 메인 로그인을 방해하지 않음</li>
 *   <li>보안 감시 및 감사 추적 용도</li>
 * </ul>
 *
 * @author SSAju Team
 * @see LoginAttemptEvent
 * @see ssafy.SSAju.service.AuthService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptEventListener {

    private final LoginAttemptRepository loginAttemptRepository;

    /**
     * 로그인 시도 이벤트를 처리하고 기록을 저장합니다.
     *
     * <p>이 메서드는 다음과 같은 특성을 가집니다:
     * <ul>
     *   <li>{@code @Async}: 별도의 스레드에서 비동기 실행</li>
     *   <li>{@code @Transactional(REQUIRES_NEW)}: 메인 트랜잭션과 독립된 새 트랜잭션 생성</li>
     *   <li>예외 발생 시 로그만 기록하고 메인 로직에 영향 없음</li>
     * </ul>
     *
     * @param event 로그인 시도 이벤트
     */
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
            loginAttemptRepository.saveAndFlush(attempt);
        } catch (Exception e) {
            log.error("로그인 시도 기록 저장 실패: {}", e.getMessage());
        }
    }
}
