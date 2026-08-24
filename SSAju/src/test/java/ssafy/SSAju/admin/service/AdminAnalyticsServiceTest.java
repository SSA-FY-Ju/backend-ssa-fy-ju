package ssafy.SSAju.admin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import ssafy.SSAju.admin.config.AdminConfig;
import ssafy.SSAju.admin.dto.AnalyticsDetailDTO;
import ssafy.SSAju.admin.dto.AnalyticsListDTO;
import ssafy.SSAju.admin.repository.AdminAnalyticsQueryRepository;
import ssafy.SSAju.career.enums.AnalysisType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import ssafy.SSAju.exception.AnalyticsNotFoundException;
import ssafy.SSAju.exception.InvalidDateRangeException;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAnalyticsService 단위 테스트")
class AdminAnalyticsServiceTest {

    @Mock
    private AdminAnalyticsQueryRepository analyticsRepository;

    @Mock
    private AdminPaginationUtil paginationUtil;

    @Mock
    private AdminConfig adminConfig;

    @InjectMocks
    private AdminAnalyticsService adminAnalyticsService;

    private void stubPagination(int size) {
        given(paginationUtil.of(anyInt(), eq(size))).willReturn(PageRequest.of(0, size));
    }

    private void stubAnalyticsRangeDays(int days) {
        AdminConfig.Pagination pagination = new AdminConfig.Pagination();
        pagination.setAnalyticsRangeDays(days);
        given(adminConfig.getPagination()).willReturn(pagination);
    }

    private AnalyticsListDTO stubListItem(long id, String type) {
        return new AnalyticsListDTO(id, 1L, type, Instant.now());
    }

    @Test
    @DisplayName("getAnalyticsHistory - 필터 없음 → 30일 기본 범위로 조회")
    void getAnalyticsHistory_noFilter_usesDefaultRange() {
        stubPagination(20);
        stubAnalyticsRangeDays(30);
        List<AnalyticsListDTO> mockList = List.of(
                stubListItem(3L, "SAJU"),
                stubListItem(2L, "CAREER_CONSULTATION")
        );
        given(analyticsRepository.findAnalyticsByDateAndType(isNull(), any(), any(), eq(0), eq(20)))
                .willReturn(mockList);

        List<AnalyticsListDTO> result = adminAnalyticsService.getAnalyticsHistory(
                null, null, null, 0, 20);

        assertThat(result).hasSize(2);
        verify(analyticsRepository).findAnalyticsByDateAndType(isNull(), any(), any(), eq(0), eq(20));
    }

    @Test
    @DisplayName("getAnalyticsHistory - dateFrom이 30일 이전이면 자동으로 범위 내로 조정")
    void getAnalyticsHistory_dateFromBeyondRange_clampsToEarliest() {
        stubPagination(20);
        stubAnalyticsRangeDays(30);
        given(analyticsRepository.findAnalyticsByDateAndType(any(), any(), any(), anyInt(), anyInt()))
                .willReturn(List.of());

        LocalDate seoulToday = LocalDate.now(AdminBaseService.SEOUL_ZONE);
        LocalDate tooEarly = seoulToday.minusDays(60);
        adminAnalyticsService.getAnalyticsHistory(null, tooEarly, null, 0, 20);

        // 실제 전달된 dateFrom은 today - 29일로 조정됨
        LocalDate expectedFrom = seoulToday.minusDays(29);
        verify(analyticsRepository).findAnalyticsByDateAndType(
                isNull(), eq(expectedFrom), any(), eq(0), eq(20));
    }

    @Test
    @DisplayName("getAnalyticsHistory - dateTo가 오늘 이후이면 오늘로 조정")
    void getAnalyticsHistory_dateToAfterToday_clampsToToday() {
        stubPagination(20);
        stubAnalyticsRangeDays(30);
        given(analyticsRepository.findAnalyticsByDateAndType(any(), any(), any(), anyInt(), anyInt()))
                .willReturn(List.of());

        LocalDate seoulToday = LocalDate.now(AdminBaseService.SEOUL_ZONE);
        LocalDate future = seoulToday.plusDays(5);
        adminAnalyticsService.getAnalyticsHistory(null, null, future, 0, 20);

        verify(analyticsRepository).findAnalyticsByDateAndType(
                isNull(), any(), eq(seoulToday), eq(0), eq(20));
    }

    @Test
    @DisplayName("getAnalyticsHistory - dateFrom > dateTo → InvalidDateRangeException 발생")
    void getAnalyticsHistory_fromAfterTo_throwsException() {
        stubAnalyticsRangeDays(30);

        LocalDate seoulToday = LocalDate.now(AdminBaseService.SEOUL_ZONE);
        LocalDate from = seoulToday.minusDays(1);
        LocalDate to = seoulToday.minusDays(5);

        assertThatThrownBy(() -> adminAnalyticsService.getAnalyticsHistory(null, from, to, 0, 20))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("시작일");

        verifyNoInteractions(analyticsRepository);
    }

    @Test
    @DisplayName("getAnalyticsHistory - type 필터 적용 → Repository에 정확히 전달")
    void getAnalyticsHistory_withTypeFilter_passesToRepository() {
        stubPagination(20);
        stubAnalyticsRangeDays(30);
        given(analyticsRepository.findAnalyticsByDateAndType(eq("SAJU"), any(), any(), anyInt(), anyInt()))
                .willReturn(List.of(stubListItem(1L, "SAJU")));

        List<AnalyticsListDTO> result = adminAnalyticsService.getAnalyticsHistory(
                AnalysisType.SAJU, null, null, 0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).analysisType()).isEqualTo("SAJU");
    }

    @Test
    @DisplayName("getAnalyticsDetail - 존재하는 id → AnalyticsDetailDTO 반환")
    void getAnalyticsDetail_existingId_returnsDto() {
        AnalyticsDetailDTO stub = new AnalyticsDetailDTO(1L, 10L, "SAJU", "{\"test\":\"data\"}", Instant.now());
        given(analyticsRepository.findAnalyticsById(1L, "SAJU", 10L)).willReturn(Optional.of(stub));

        AnalyticsDetailDTO result = adminAnalyticsService.getAnalyticsDetail(1L, AnalysisType.SAJU, 10L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.jsonData()).isEqualTo("{\"test\":\"data\"}");
    }

    @Test
    @DisplayName("getAnalyticsDetail - 존재하지 않는 id → AnalyticsNotFoundException 발생")
    void getAnalyticsDetail_notFound_throwsException() {
        given(analyticsRepository.findAnalyticsById(999L, "SAJU", 10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminAnalyticsService.getAnalyticsDetail(999L, AnalysisType.SAJU, 10L))
                .isInstanceOf(AnalyticsNotFoundException.class)
                .hasMessageContaining("999");
    }
}
