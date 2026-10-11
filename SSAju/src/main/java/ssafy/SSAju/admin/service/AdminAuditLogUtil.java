package ssafy.SSAju.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ssafy.SSAju.admin.dto.AdjustmentAction;
import ssafy.SSAju.admin.enums.AdminLoginFailureReason;
import ssafy.SSAju.util.TokenHashUtil;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditLogUtil {

    private final Clock clock;
    private final AtomicLong recordingFailures = new AtomicLong();

    public void loginSucceeded(Long adminId) {
        afterCommit(() -> log.info("[AUDIT] action=validateAdminCredentials, userId={}, status=SUCCESS, timestamp={}",
                adminId, clock.instant()));
    }

    public void loginFailed(String email, AdminLoginFailureReason reason) {
        safelyRecord(() -> log.warn("[AUDIT] action=validateAdminCredentials, emailHash={}, status=FAILURE, reason={}, timestamp={}",
                TokenHashUtil.sha256(email), reason, clock.instant()));
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
                    safelyRecord(record);
                }
            });
        } else {
            safelyRecord(record);
        }
    }

    public long recordingFailureCount() {
        return recordingFailures.get();
    }

    private void safelyRecord(Runnable record) {
        try {
            record.run();
        } catch (RuntimeException e) {
            long failures = recordingFailures.incrementAndGet();
            try {
                log.error("[AUDIT] recordingFailureCount={}, exceptionType={}", failures, e.getClass().getSimpleName());
            } catch (RuntimeException reportingFailure) {
                // 로그 출력 자체가 실패해도 카운터는 남기고 이미 확정된 업무 결과를 유지한다.
            }
        }
    }
}
