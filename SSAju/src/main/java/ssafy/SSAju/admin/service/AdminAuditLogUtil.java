package ssafy.SSAju.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ssafy.SSAju.admin.dto.AdjustmentAction;
import ssafy.SSAju.util.TokenHashUtil;

import java.time.Clock;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditLogUtil {

    public enum LoginFailureReason { INVALID_EMAIL, WRONG_PASSWORD, NOT_ADMIN }

    private final Clock clock;

    public void loginSucceeded(Long adminId) {
        afterCommit(() -> log.info("[AUDIT] action=validateAdminCredentials, userId={}, status=SUCCESS, timestamp={}",
                adminId, clock.instant()));
    }

    public void loginFailed(String email, LoginFailureReason reason) {
        log.warn("[AUDIT] action=validateAdminCredentials, emailHash={}, status=FAILURE, reason={}, timestamp={}",
                TokenHashUtil.sha256(email), reason, clock.instant());
    }

    public void usageAdjusted(Long userId, AdjustmentAction action, int before, int after) {
        afterCommit(() -> log.info("일일 사용량 조정 완료: userId={}, action={}, before={}, after={}, timestamp={}",
                userId, action, before, after, clock.instant()));
    }

    private void afterCommit(Runnable record) {
        // 호출자의 바깥 트랜잭션이 롤백되면 성공 로그도 남기지 않는다.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    record.run();
                }
            });
        } else {
            record.run();
        }
    }
}
