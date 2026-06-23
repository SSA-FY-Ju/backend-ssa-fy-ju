package ssafy.SSAju.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.admin.config.AdminConfig;
import ssafy.SSAju.admin.dto.AnalyticsDetailDTO;
import ssafy.SSAju.admin.dto.AnalyticsListDTO;
import ssafy.SSAju.admin.repository.AdminAnalyticsQueryRepository;
import ssafy.SSAju.exception.AnalyticsNotFoundException;
import ssafy.SSAju.exception.InvalidDateRangeException;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAnalyticsService extends AdminBaseService {

    private final AdminAnalyticsQueryRepository analyticsRepository;
    private final AdminPaginationUtil paginationUtil;
    private final AdminConfig adminConfig;

    public List<AnalyticsListDTO> getAnalyticsHistory(
            String analysisType, LocalDate dateFrom, LocalDate dateTo, int page, int size) {

        LocalDate today = todaySeoul();
        LocalDate effectiveDateFrom = resolveFrom(dateFrom, today);
        LocalDate effectiveDateTo = resolveTo(dateTo, today);
        validateDateRange(effectiveDateFrom, effectiveDateTo);

        int validSize = paginationUtil.of(page, size).getPageSize();
        int validPage = Math.max(page, 0);

        List<AnalyticsListDTO> result = analyticsRepository.findAnalyticsByDateAndType(
                analysisType, effectiveDateFrom, effectiveDateTo, validPage, validSize);

        log.debug("분석 기록 조회: type={}, dateFrom={}, dateTo={}, page={}, size={}, count={}",
                analysisType, effectiveDateFrom, effectiveDateTo, validPage, validSize, result.size());
        return result;
    }

    public AnalyticsDetailDTO getAnalyticsDetail(Long id, String analysisType) {
        return analyticsRepository.findAnalyticsById(id, analysisType)
                .orElseThrow(() -> new AnalyticsNotFoundException(
                        "분석 기록을 찾을 수 없습니다: id=" + id + ", type=" + analysisType));
    }

    // 30일 범위 제한: dateFrom이 최대 범위를 벗어나면 자동으로 범위 내로 조정
    private LocalDate resolveFrom(LocalDate dateFrom, LocalDate today) {
        LocalDate earliest = today.minusDays(adminConfig.getPagination().getAnalyticsRangeDays() - 1L);
        if (dateFrom == null) return earliest;
        return dateFrom.isBefore(earliest) ? earliest : dateFrom;
    }

    private LocalDate resolveTo(LocalDate dateTo, LocalDate today) {
        if (dateTo == null) return today;
        return dateTo.isAfter(today) ? today : dateTo;
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidDateRangeException("시작일이 종료일보다 늦을 수 없습니다.");
        }
    }
}
