package ssafy.SSAju.admin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.admin.dto.AdjustmentAction;
import ssafy.SSAju.admin.dto.UsageAdjustmentRequestDTO;
import ssafy.SSAju.admin.dto.UsageAdjustmentResponseDTO;
import ssafy.SSAju.admin.dto.UserSearchDTO;
import ssafy.SSAju.admin.repository.AdminDailyUsageQueryRepository;
import ssafy.SSAju.admin.repository.AdminUserQueryRepository;
import ssafy.SSAju.entity.DailyApiUsage;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.UserNotFoundException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUsageAdjustmentService 단위 테스트")
class AdminUsageAdjustmentServiceTest {

    @Mock
    private AdminDailyUsageQueryRepository dailyUsageRepository;

    @Mock
    private AdminUserQueryRepository adminUserQueryRepository;

    @InjectMocks
    private AdminUsageAdjustmentService usageAdjustmentService;

    private UserSearchDTO stubUserSearchDTO(long id) {
        return new UserSearchDTO(id, "test@test.com", "테스트유저",
                Instant.now(), UserStatus.ACTIVE, null, 0L);
    }

    private DailyApiUsage stubUsage(int count) {
        DailyApiUsage usage = mock(DailyApiUsage.class);
        given(usage.getRequestCount()).willReturn(count);
        return usage;
    }

    private void stubUserExists(long userId) {
        given(adminUserQueryRepository.findUserById(userId))
                .willReturn(Optional.of(stubUserSearchDTO(userId)));
    }

    @Test
    @DisplayName("adjustDailyUsage - RESET → usageCountAfter == 0")
    void adjustDailyUsage_reset_setsCountToZero() {
        stubUserExists(1L);
        DailyApiUsage usage = stubUsage(3);
        given(dailyUsageRepository.findUsageByUserAndDate(eq(1L), any())).willReturn(Optional.of(usage));
        given(dailyUsageRepository.resetDailyUsage(eq(1L), any())).willReturn(1);

        UsageAdjustmentResponseDTO result = usageAdjustmentService.adjustDailyUsage(
                1L, new UsageAdjustmentRequestDTO(AdjustmentAction.RESET, null));

        assertThat(result.usageCountBefore()).isEqualTo(3);
        assertThat(result.usageCountAfter()).isEqualTo(0);
        assertThat(result.action()).isEqualTo("RESET");
    }

    @Test
    @DisplayName("adjustDailyUsage - DECREMENT 1 → usageCountAfter == before - 1")
    void adjustDailyUsage_decrement_reducesCount() {
        stubUserExists(1L);
        DailyApiUsage usage = stubUsage(3);
        given(dailyUsageRepository.findUsageByUserAndDate(eq(1L), any())).willReturn(Optional.of(usage));
        given(dailyUsageRepository.decrementDailyUsage(eq(1L), any(), eq(1))).willReturn(1);

        UsageAdjustmentResponseDTO result = usageAdjustmentService.adjustDailyUsage(
                1L, new UsageAdjustmentRequestDTO(AdjustmentAction.DECREMENT, 1));

        assertThat(result.usageCountBefore()).isEqualTo(3);
        assertThat(result.usageCountAfter()).isEqualTo(2);
    }

    @Test
    @DisplayName("adjustDailyUsage - DECREMENT amount > current → after는 0 (음수 방지)")
    void adjustDailyUsage_decrementExceedsCount_afterIsZero() {
        stubUserExists(1L);
        DailyApiUsage usage = stubUsage(1);
        given(dailyUsageRepository.findUsageByUserAndDate(eq(1L), any())).willReturn(Optional.of(usage));
        given(dailyUsageRepository.decrementDailyUsage(eq(1L), any(), eq(5))).willReturn(1);

        UsageAdjustmentResponseDTO result = usageAdjustmentService.adjustDailyUsage(
                1L, new UsageAdjustmentRequestDTO(AdjustmentAction.DECREMENT, 5));

        assertThat(result.usageCountAfter()).isEqualTo(0);
    }

    @Test
    @DisplayName("adjustDailyUsage - 오늘 사용량 레코드 없음 → before/after 모두 0 반환")
    void adjustDailyUsage_noRecord_returnsBothZero() {
        stubUserExists(1L);
        given(dailyUsageRepository.findUsageByUserAndDate(eq(1L), any())).willReturn(Optional.empty());

        UsageAdjustmentResponseDTO result = usageAdjustmentService.adjustDailyUsage(
                1L, new UsageAdjustmentRequestDTO(AdjustmentAction.RESET, null));

        assertThat(result.usageCountBefore()).isEqualTo(0);
        assertThat(result.usageCountAfter()).isEqualTo(0);
        verify(dailyUsageRepository, never()).resetDailyUsage(any(), any());
    }

    @Test
    @DisplayName("adjustDailyUsage - 존재하지 않는 userId → UserNotFoundException 발생")
    void adjustDailyUsage_userNotFound_throwsException() {
        given(adminUserQueryRepository.findUserById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> usageAdjustmentService.adjustDailyUsage(
                999L, new UsageAdjustmentRequestDTO(AdjustmentAction.RESET, null)))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("adjustDailyUsage - DECREMENT, amount == 0 → IllegalArgumentException 발생")
    void adjustDailyUsage_decrementAmountZero_throwsException() {
        assertThatThrownBy(() -> usageAdjustmentService.adjustDailyUsage(
                1L, new UsageAdjustmentRequestDTO(AdjustmentAction.DECREMENT, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount는 1 이상");
    }

    @Test
    @DisplayName("adjustDailyUsage - DECREMENT, amount null → IllegalArgumentException 발생")
    void adjustDailyUsage_decrementAmountNull_throwsException() {
        assertThatThrownBy(() -> usageAdjustmentService.adjustDailyUsage(
                1L, new UsageAdjustmentRequestDTO(AdjustmentAction.DECREMENT, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount는 1 이상");
    }

    @Test
    @DisplayName("adjustDailyUsage - action null → IllegalArgumentException 발생")
    void adjustDailyUsage_actionNull_throwsException() {
        assertThatThrownBy(() -> usageAdjustmentService.adjustDailyUsage(
                1L, new UsageAdjustmentRequestDTO(null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action은 필수");
    }
}
