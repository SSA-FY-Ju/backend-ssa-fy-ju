package ssafy.SSAju.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.admin.dto.UsageAdjustmentRequestDTO;
import ssafy.SSAju.admin.dto.UsageAdjustmentResponseDTO;
import ssafy.SSAju.admin.repository.AdminDailyUsageQueryRepository;
import ssafy.SSAju.admin.repository.AdminUserQueryRepository;
import ssafy.SSAju.entity.DailyApiUsage;
import ssafy.SSAju.exception.UserNotFoundException;

import java.time.LocalDate;
import java.util.Optional;

// NOTE: 단일 관리자 기준으로 구현됨. 다중 관리자 환경으로 확장 시 SELECT FOR UPDATE 적용 필요.
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUsageAdjustmentService extends AdminBaseService {

    private static final String ACTION_RESET = "RESET";
    private static final String ACTION_DECREMENT = "DECREMENT";

    private final AdminDailyUsageQueryRepository dailyUsageRepository;
    private final AdminUserQueryRepository adminUserQueryRepository;

    @Transactional
    public UsageAdjustmentResponseDTO adjustDailyUsage(Long userId, UsageAdjustmentRequestDTO request) {
        validateRequest(request);
        validateUserExists(userId);

        LocalDate today = todaySeoul();

        Optional<DailyApiUsage> usageOpt = dailyUsageRepository.findUsageByUserAndDate(userId, today);
        int before = usageOpt.map(DailyApiUsage::getRequestCount).orElse(0);

        if (usageOpt.isEmpty()) {
            log.debug("오늘 일일 사용량 레코드 없음: userId={}, date={}", userId, today);
            return new UsageAdjustmentResponseDTO(userId, today.toString(), 0, 0, request.action());
        }

        int after;
        if (ACTION_RESET.equals(request.action())) {
            dailyUsageRepository.resetDailyUsage(userId, today);
            after = 0;
        } else {
            dailyUsageRepository.decrementDailyUsage(userId, today, request.amount());
            after = Math.max(0, before - request.amount());
        }

        log.debug("일일 사용량 조정 완료: userId={}, action={}, before={}, after={}",
                userId, request.action(), before, after);
        return new UsageAdjustmentResponseDTO(userId, today.toString(), before, after, request.action());
    }

    private void validateRequest(UsageAdjustmentRequestDTO request) {
        if (request.action() == null || request.action().isBlank()) {
            throw new IllegalArgumentException("action은 필수입니다.");
        }
        if (!ACTION_RESET.equals(request.action()) && !ACTION_DECREMENT.equals(request.action())) {
            throw new IllegalArgumentException("action은 RESET 또는 DECREMENT 이어야 합니다.");
        }
        if (ACTION_DECREMENT.equals(request.action())) {
            if (request.amount() == null || request.amount() <= 0) {
                throw new IllegalArgumentException("DECREMENT 시 amount는 1 이상이어야 합니다.");
            }
        }
    }

    // AdminUserQueryRepository: native SQL로 soft-deleted 유저도 조회 가능
    private void validateUserExists(Long userId) {
        adminUserQueryRepository.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException("userId=" + userId));
    }
}
