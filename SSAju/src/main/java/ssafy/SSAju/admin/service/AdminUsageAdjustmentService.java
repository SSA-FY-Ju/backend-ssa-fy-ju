package ssafy.SSAju.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.admin.dto.AdjustmentAction;
import ssafy.SSAju.admin.dto.UsageAdjustmentRequestDTO;
import ssafy.SSAju.admin.dto.UsageAdjustmentResponseDTO;
import ssafy.SSAju.admin.repository.AdminDailyUsageQueryRepository;
import ssafy.SSAju.admin.repository.AdminUserQueryRepository;
import ssafy.SSAju.annotation.AuditLog;
import ssafy.SSAju.entity.DailyApiUsage;
import ssafy.SSAju.exception.UserNotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

// NOTE: 단일 관리자 기준으로 구현됨. 다중 관리자 환경으로 확장 시 SELECT FOR UPDATE 적용 필요.
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUsageAdjustmentService extends AdminBaseService {

    private final AdminDailyUsageQueryRepository dailyUsageRepository;
    private final AdminUserQueryRepository adminUserQueryRepository;

    // 관리자가 유저의 일일 제한을 직접 변경하는 민감 작업 → 구조화 로깅(INFO) + @AuditLog 감사 로그 적용
    @Transactional
    @AuditLog
    public UsageAdjustmentResponseDTO adjustDailyUsage(Long userId, UsageAdjustmentRequestDTO request) {
        Instant start = Instant.now();
        validateRequest(request);
        validateUserExists(userId);

        LocalDate today = todaySeoul();

        Optional<DailyApiUsage> usageOpt = dailyUsageRepository.findUsageByUserAndDate(userId, today);
        int before = usageOpt.map(DailyApiUsage::getRequestCount).orElse(0);

        if (usageOpt.isEmpty()) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.info("일일 사용량 조정 시도: userId={}, action={}, 사용 기록 없음(before=0, after=0), durationMs={}",
                    userId, request.action(), durationMs);
            return new UsageAdjustmentResponseDTO(userId, today.toString(), 0, 0, request.action().name());
        }

        int after;
        if (request.action() == AdjustmentAction.RESET) {
            dailyUsageRepository.resetDailyUsage(userId, today);
            after = 0;
        } else {
            dailyUsageRepository.decrementDailyUsage(userId, today, request.amount());
            after = Math.max(0, before - request.amount());
        }

        long durationMs = Duration.between(start, Instant.now()).toMillis();
        log.info("일일 사용량 조정 완료: userId={}, action={}, before={}, after={}, durationMs={}",
                userId, request.action(), before, after, durationMs);
        return new UsageAdjustmentResponseDTO(userId, today.toString(), before, after, request.action().name());
    }

    private void validateRequest(UsageAdjustmentRequestDTO request) {
        if (request.action() == null) {
            throw new IllegalArgumentException("action은 필수입니다.");
        }
        if (request.action() == AdjustmentAction.DECREMENT
                && (request.amount() == null || request.amount() <= 0)) {
            throw new IllegalArgumentException("DECREMENT 시 amount는 1 이상이어야 합니다.");
        }
    }

    // AdminUserQueryRepository: native SQL로 soft-deleted 유저도 조회 가능
    private void validateUserExists(Long userId) {
        adminUserQueryRepository.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException("userId=" + userId));
    }
}
